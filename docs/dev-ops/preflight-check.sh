#!/usr/bin/env bash
# ==============================================================================
# docs/dev-ops/preflight-check.sh —— 中间件环境「启动前自检」
# ==============================================================================
# 背景（2026-09-20 两次真实事故）：
#   1) docs/dev-ops/ 曾被整目录 .gitignore 忽略 → pgvector/sql/init.sql、
#      redis/redis.conf、mysql/my.cnf 丢失后**无法从 git 恢复**。
#      更坑的是 Docker Desktop 挂载「不存在的宿主机文件」时会**自动创建同名空目录**占位，
#      所以容器报的是 "not a directory"，掩盖了「文件早已丢失」的事实。
#   2) 同一原因导致用户端演示页 docs/dev-ops/nginx/html/index.html 丢失，打开即 404。
#      （2026-09-26 用户端/管理端合并重构后，该静态演示页与 start-ui-user.bat 已删除，
#        前端统一为 zhishu-ui，故本节改为校验 zhishu-ui 的关键文件与目录。）
#
# 本脚本的作用：在起容器 / 起前端之前，先把「所有被引用的文件是否真的存在且非空」查一遍，
# 让问题在**启动前**以明确文案暴露，而不是等到容器启动失败或浏览器 404。
#
# 用法：
#   bash docs/dev-ops/preflight-check.sh          # 自检（核心栈失败则退出码 1）
#   bash docs/dev-ops/preflight-check.sh -v       # 同时列出可选栈的缺失明细
# ==============================================================================

set -uo pipefail

VERBOSE=0
[ "${1:-}" = "-v" ] && VERBOSE=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT" || exit 2

FAIL=0
WARN=0
PASS=0

ok()   { printf '  \033[32m[ OK ]\033[0m %s\n' "$1"; PASS=$((PASS + 1)); }
bad()  { printf '  \033[31m[FAIL]\033[0m %s\n' "$1"; FAIL=$((FAIL + 1)); }
warn() { printf '  \033[33m[WARN]\033[0m %s\n' "$1"; WARN=$((WARN + 1)); }

# 检查单个文件：必须存在、是普通文件、且非空
check_file() {
    local p="$1" label="${2:-$1}"
    if [ ! -e "$p" ]; then
        bad "$label 缺失（$p）"
        echo "         → 若它是挂载源，Docker 会创建同名空目录占位，务必先补文件"
    elif [ -d "$p" ]; then
        bad "$label 是目录而非文件（$p）—— 典型「Docker 空目录占位」症状"
        echo "         → 先确认目录条目数为 0，再 rmdir 后重建文件"
    elif [ ! -s "$p" ]; then
        bad "$label 是空文件（$p）"
    else
        ok "$label  ($(wc -c < "$p") bytes)"
    fi
}

echo "=============================================================="
echo " 中间件环境启动前自检    repo: $ROOT"
echo "=============================================================="

# ------------------------------------------------------------------------------
echo
echo "【1/4】核心中间件配置（丢失后无法重建，必须存在）"
# ------------------------------------------------------------------------------
check_file "docs/dev-ops/mysql/my.cnf"          "MySQL 配置"
check_file "docs/dev-ops/redis/redis.conf"      "Redis 配置"
check_file "docs/dev-ops/pgvector/sql/init.sql" "pgvector 初始化 SQL"

# ------------------------------------------------------------------------------
echo
echo "【2/4】核心栈 compose 的挂载源（docker-compose-environment*.yml）"
# ------------------------------------------------------------------------------
for cf in docs/dev-ops/docker-compose-environment.yml docs/dev-ops/docker-compose-environment-aliyun.yml; do
    [ -f "$cf" ] || { warn "编排文件不存在，跳过：$cf"; continue; }
    echo "  ── $(basename "$cf")"
    # 抓形如 "  - ./path:/container/path" 的挂载源，去掉行首符号
    # ⚠ 必须用进程替换而非管道：管道里的 while 处于子 shell，FAIL 累加不会传回父 shell
    while read -r src; do
        [ -n "$src" ] || continue
        full="docs/dev-ops/${src#./}"
        if [ ! -e "$full" ]; then
            bad "挂载源缺失：$src  ->  $full"
            echo "         → Docker 会把它建成空目录占位，务必先补文件"
            FAIL=$((FAIL + 1))
        elif [ -d "$full" ] && [ "$(find "$full" -mindepth 1 2>/dev/null | wc -l)" -eq 0 ]; then
            warn "挂载目录为空：$src（若本应有初始化脚本，说明已丢失）"
        else
            ok "挂载源存在：$src"
        fi
    done < <(grep -oE '^[[:space:]]*-[[:space:]]+\./[^:]+' "$cf" \
                 | sed -E 's/^[[:space:]]*-[[:space:]]+//' | sort -u)
done

# ------------------------------------------------------------------------------
echo
echo "【3/4】前端工程 zhishu-ui（用户端 + 管理端合并后的唯一前端）"
# ------------------------------------------------------------------------------
# 2026-09-26：原「用户端演示页」docs/dev-ops/nginx/html 与 start-ui-user.bat 已删除，
# 前端统一为 zhishu-ui（Vite + Vue3）。起服务前先确认入口与关键目录没被误删 ——
# 少一个目录的表现是「白屏 / 路由 404」，而不是明确的构建报错。
FE="zhishu-ui"
check_file "$FE/package.json"         "前端依赖清单"
check_file "$FE/index.html"           "前端入口 HTML"
check_file "$FE/vite.config.ts"       "Vite 配置（/api 代理到 8099）"
check_file "$FE/src/main.ts"          "前端入口脚本"
check_file "$FE/src/router/routes.ts" "路由表（登录 / 用户端 / 管理端）"
for d in "$FE/src/views/user" "$FE/src/views/admin" "$FE/src/views/sys" "$FE/src/layouts"; do
    if [ -d "$d" ]; then
        ok "前端目录 $d"
    else
        bad "前端目录缺失（$d）"
        FAIL=$((FAIL + 1))
    fi
done

# ------------------------------------------------------------------------------
echo
echo "【4/4】可选栈（ELK / Prometheus+Grafana / MCP）——缺失只提示，不算失败"
# ------------------------------------------------------------------------------
OPTIONAL=(
    "docs/dev-ops/logstash/logstash.conf"
    "docs/dev-ops/kibana/config/kibana.yml"
    "docs/dev-ops/prometheus/prometheus.yml"
)
OPT_MISSING=0
for p in "${OPTIONAL[@]}"; do
    if [ -f "$p" ] && [ -s "$p" ]; then
        [ "$VERBOSE" = 1 ] && ok "$p"
    else
        OPT_MISSING=$((OPT_MISSING + 1))
        [ "$VERBOSE" = 1 ] && warn "未就绪：$p"
    fi
done
if [ "$OPT_MISSING" -eq 0 ]; then
    ok "可选栈配置齐全"
else
    warn "可选栈有 $OPT_MISSING 个配置未就绪（ELK / Prometheus / Kibana）——不影响核心栈，需用时按课程材料重建"
fi
if [ "$VERBOSE" != 1 ]; then
    echo "        （加 -v 查看明细）"
fi

# ------------------------------------------------------------------------------
echo
echo "=============================================================="
printf ' 结果：OK=%d  WARN=%d  FAIL=%d\n' "$PASS" "$WARN" "$FAIL"
if [ "$FAIL" -gt 0 ]; then
    echo " ⚠ 存在阻塞项，请先补齐再启动容器/前端。"
    echo "=============================================================="
    exit 1
fi
echo " ✓ 核心环境自检通过。"
echo "=============================================================="
exit 0
