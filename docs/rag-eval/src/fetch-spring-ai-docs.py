#!/usr/bin/env python3
"""
拉取 Spring AI 官方文档（AsciiDoc）并转成纯文本，落到 docs/rag-eval/corpus/spring-ai-docs/。

为什么用 raw 通道而不是 GitHub API：
  未认证的 api.github.com 限流 60 次/小时，121 个文件会被掐；
  raw.githubusercontent.com 对单文件读取没有这个限制。

转换策略（刻意做得很轻，不手工清洗）：
  - 只去掉 `include::xxx[]` 指令行（它们指向本仓库其它文件，单独取出会变成悬空引用）
  - 保留 NOTE: / TIP: / WARNING: / [[anchor]] / [source,java] / ---- 代码围栏等原始标记
  - 原因：语料越"干净教科书化"，检索指标越虚高（见计划 §1.3）。真实文档就是带这些标记的。
"""
import json
import os
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

REPO = "spring-projects/spring-ai"
# ⚠️ 必须与项目依赖的 Spring AI 版本一致（根 pom 的 spring-ai BOM = 1.1.8）。
#    main 分支是 2.0.x，文档里写着 "Spring AI 2.0.x supports Spring Boot 4.0.x"，
#    与项目 1.1.8 的 API 对不上 —— 面试被问「你知识库是哪个版本的文档」会露。
REF = os.environ.get("SPRING_AI_REF", "v1.1.8")
PAGES_PREFIX = "spring-ai-docs/src/main/antora/modules/ROOT/pages/"
RAW = f"https://raw.githubusercontent.com/{REPO}/{REF}/"

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.normpath(os.path.join(HERE, "..", "corpus", "spring-ai-docs"))

# 排除纯变更日志：145KB 的 upgrade-notes 会把 chunk 分布带偏，且它不是"技术文档"语义
EXCLUDE = {"upgrade-notes.adoc"}


def list_adoc_paths(tree_json_path):
    with open(tree_json_path, encoding="utf-8") as f:
        tree = json.load(f)
    out = []
    for e in tree["tree"]:
        if e["type"] != "blob":
            continue
        p = e["path"]
        if not p.startswith(PAGES_PREFIX) or not p.endswith(".adoc"):
            continue
        rel = p[len(PAGES_PREFIX):]
        if rel in EXCLUDE:
            continue
        out.append((rel, e["size"]))
    return sorted(out)


def to_plain_text(src: str) -> str:
    lines = []
    for ln in src.splitlines():
        # 去掉 include 指令（含带属性/标签的形式）
        if re.match(r"^\s*include::[^\[]+\[.*\]\s*$", ln):
            continue
        lines.append(ln)
    text = "\n".join(lines)
    # 收敛 3 个以上连续空行
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip() + "\n"


def fetch(rel):
    url = RAW + PAGES_PREFIX + rel
    try:
        with urllib.request.urlopen(url, timeout=45) as r:
            raw = r.read().decode("utf-8", errors="replace")
    except Exception as e:
        return rel, None, f"{type(e).__name__}: {e}"

    text = to_plain_text(raw)
    dest = os.path.join(OUT_DIR, rel[:-len(".adoc")] + ".txt")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    return rel, len(text), None


def main():
    tree_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "spring-ai-tree.json")
    if not os.path.exists(tree_path):
        print(f"缺少 tree json: {tree_path}", file=sys.stderr)
        print("先生成：curl -s 'https://api.github.com/repos/spring-projects/spring-ai/git/trees/main?recursive=1' -o spring-ai-tree.json", file=sys.stderr)
        return 2

    items = list_adoc_paths(tree_path)
    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"待拉取 {len(items)} 个文件 -> {OUT_DIR}")

    ok = 0
    failed = []
    total_chars = 0
    with ThreadPoolExecutor(max_workers=8) as ex:
        for rel, size, err in ex.map(fetch, [r for r, _ in items]):
            if err:
                failed.append((rel, err))
                print(f"  FAIL  {rel}  {err}")
            else:
                ok += 1
                total_chars += size

    print(f"\n成功 {ok} / {len(items)}，纯文本合计 {total_chars} 字符")
    if failed:
        print(f"失败 {len(failed)} 个：")
        for rel, err in failed:
            print(f"  {rel}  {err}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
