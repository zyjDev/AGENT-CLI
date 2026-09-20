#!/usr/bin/env bash
# ==============================================================================
# sync-origin-ref.sh —— 修复 origin/main 跟踪引用（本机 git 环境缺陷的绕过方案）
# ==============================================================================
# 【问题】
#   本机 git（2.55.0.windows.3）**无法写入 refs/remotes/* 的松散引用**：
#     $ git update-ref refs/remotes/rt-test/main <sha>   # exit=0，但文件根本没生成
#     $ git update-ref refs/heads/rh-test        <sha>   # exit=0，文件正常生成
#   即 git 对 refs/remotes/* 的写入是**静默 no-op**。更麻烦的是 git fetch 还会
#   把已存在的松散引用删掉。后果：
#     - git status 报 `## main...origin/main [gone]`
#     - 每次 push 后报 `[ahead 1]`（跟踪引用没跟着前进）
#     - git rev-parse origin/main 报 unknown revision
#   但 push / commit / 对象库 / 远端**全部正常**，只是这个引用维护不了。
#
# 【绕过原理】
#   packed-refs 里的条目 git fetch **完全不碰**（实测 fetch 前后 mtime 不变），
#   且 packed-refs 里的引用可以被正常解析。因此把 origin/main 放进 packed-refs
#   就能稳定存在 —— 实测 fetch 之后依然存活。
#
# 【用法】
#   bash sync-origin-ref.sh              # 同步 origin/main
#   bash sync-origin-ref.sh <remote>     # 指定 remote（默认 origin）
#   bash sync-origin-ref.sh origin dev   # 指定 remote + 分支
# ==============================================================================

set -uo pipefail

REMOTE="${1:-origin}"
BRANCH="${2:-main}"

cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "不在 git 仓库内"; exit 2; }

REF="refs/remotes/${REMOTE}/${BRANCH}"

echo "==> 查询远端 ${REMOTE}/${BRANCH} ..."
SHA="$(git ls-remote --heads "$REMOTE" "refs/heads/${BRANCH}" 2>/dev/null | cut -f1 | head -1)"
if [ -z "$SHA" ]; then
    echo "    ✗ 无法获取远端 SHA（远端名或分支名是否正确？网络是否可达？）"
    exit 1
fi
echo "    远端 SHA = $SHA"

PACKED=".git/packed-refs"

# 1) 清掉 packed-refs 里该引用的旧条目（有则删）
if [ -f "$PACKED" ] && grep -q "	${REF}$\| ${REF}$" "$PACKED" 2>/dev/null; then
    echo "==> 移除 packed-refs 中的旧条目"
    grep -v " ${REF}$" "$PACKED" > "${PACKED}.tmp" && mv "${PACKED}.tmp" "$PACKED"
fi

# 2) 追加新条目
echo "==> 写入 packed-refs"
printf '%s %s\n' "$SHA" "$REF" >> "$PACKED"

# 3) 同时尝试写松散引用（正常环境这里就够了；本机是 no-op，无害）
mkdir -p ".git/refs/remotes/${REMOTE}" 2>/dev/null
printf '%s\n' "$SHA" > ".git/refs/remotes/${REMOTE}/${BRANCH}" 2>/dev/null || true

# 4) 校验
echo "==> 校验"
RESOLVED="$(git rev-parse --short "$REF" 2>/dev/null)"
if [ "$RESOLVED" = "$(echo "$SHA" | cut -c1-7)" ]; then
    echo "    ✓ $REF -> $RESOLVED"
else
    echo "    ✗ 解析结果异常：${RESOLVED:-<无法解析>}"
    exit 1
fi

git status -sb | head -1 | sed 's/^/    git status: /'
exit 0
