#!/usr/bin/env python3
"""在 chunk 快照（JSONL）里检索片段，用于人工/半自动构建 golden set。

为什么不用 grep 直接搜 JSONL：快照里 content 的换行是转义过的 (\\n)，
grep 只能给出一整行（几千字符），看不出命中的上下文，也没法定位指纹。

用法：
    python docs/rag-eval/src/chunk-search.py <snapshot.jsonl> <regex> [--ctx 160] [--max 20]
    python docs/rag-eval/src/chunk-search.py <snapshot.jsonl> <regex> --list

选项：
    --ctx N   命中处前后各截 N 个字符（默认 160）
    --max M   最多输出 M 条命中（默认 20）
    --list    只列 source / charLen / 命中次数，不打印上下文
    --raw     不做任何清洗，原样打印命中窗口（默认会把连续空白压成单空格）
"""
import argparse
import json
import re
import sys


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("snapshot")
    ap.add_argument("pattern")
    ap.add_argument("--ctx", type=int, default=160)
    ap.add_argument("--max", type=int, default=20)
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--raw", action="store_true")
    args = ap.parse_args()

    try:
        rx = re.compile(args.pattern, re.IGNORECASE)
    except re.error as e:
        print(f"正则不合法: {e}", file=sys.stderr)
        return 2

    shown = 0
    total_hits = 0
    chunk_hits = 0
    with open(args.snapshot, encoding="utf-8") as f:
        for lineno, line in enumerate(f, 1):
            line = line.strip()
            if not line:
                continue
            rec = json.loads(line)
            content = rec["content"]
            ms = list(rx.finditer(content))
            if not ms:
                continue
            chunk_hits += 1
            total_hits += len(ms)
            if shown >= args.max:
                continue
            shown += 1
            if args.list:
                print(f"[{rec['source']}] charLen={rec['charLen']} hits={len(ms)} chunkId={rec['chunkId']}")
                continue
            print("=" * 100)
            print(f"[{rec['source']}]  charLen={rec['charLen']}  hits={len(ms)}  chunkId={rec['chunkId']}")
            for m in ms[:5]:
                s = max(0, m.start() - args.ctx)
                e = min(len(content), m.end() + args.ctx)
                window = content[s:e]
                if not args.raw:
                    window = re.sub(r"\s+", " ", window)
                mark = "" if s == 0 else "…"
                tail = "" if e >= len(content) else "…"
                print(f"  @{m.start():>6}  {mark}{window}{tail}")
            print()

    print(f"-- 命中 chunk {chunk_hits} 个 / 匹配 {total_hits} 处（已展示 {shown} 个）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
