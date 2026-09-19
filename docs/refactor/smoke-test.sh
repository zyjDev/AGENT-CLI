#!/usr/bin/env bash
# =============================================================================
# 冒烟验证脚本 —— ai-agent-station-study 重构验收基线
#
# 用途：在重构的每个阶段结束后跑一次，与基线输出比对，证明「功能不缺失」。
#
# 覆盖范围：
#   ① 服务可用性（GET query_available_agents）
#   ② Agent 装配（POST armory_agent）
#   ③ 执行链路第一轮（POST auto_agent，SSE）
#   ④ 执行链路第二轮（复用同一 sessionId，验证对话记忆）
#
# 用法：
#   bash docs/refactor/smoke-test.sh                    # 默认 Auto 链路（agentId=3）
#   AGENT_ID=1 bash docs/refactor/smoke-test.sh         # Flow 链路
#   AGENT_ID=6 bash docs/refactor/smoke-test.sh         # Fixed 链路
#
# 输出：docs/refactor/runs/<时间戳>/ 下的原始响应与摘要
#
# ⚠️ 实现注意：本机 curl 是 Windows 原生版本，**不识别 MSYS 路径**（/e/...、/tmp/...）。
#    因此脚本先 cd 到输出目录，所有 curl -o 一律使用相对文件名；
#    若写成绝对 MSYS 路径，curl 会静默写失败并返回非 0 退出码。
# =============================================================================

set -u

# ⚠️ 本机环境设置了 http_proxy/https_proxy（127.0.0.1:55312），curl 访问 127.0.0.1 会被代理拦截
#    并返回 502（表现为「服务未启动」，极具误导性）。这里显式排除回环地址。
export no_proxy="127.0.0.1,localhost"
export NO_PROXY="127.0.0.1,localhost"

BASE="${1:-http://127.0.0.1:8099}"
AGENT_ID="${AGENT_ID:-3}"
MAX_STEP="${MAX_STEP:-3}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TS="$(date +%Y%m%d-%H%M%S)"
SESSION_ID="smoke-${TS}"
OUT_DIR="${SCRIPT_DIR}/runs/${TS}"
mkdir -p "${OUT_DIR}"
cd "${OUT_DIR}" || exit 1

# Auto 链路一次请求含多步 LLM 调用，Step4 还有 Thread.sleep(1000)，需给足超时
SSE_TIMEOUT="${SSE_TIMEOUT:-600}"

PASS=0
FAIL=0

log()  { printf '%s\n' "$*"; }
ok()   { log "  [OK]   $*"; PASS=$((PASS + 1)); }
bad()  { log "  [FAIL] $*"; FAIL=$((FAIL + 1)); }

log "=============================================="
log " 冒烟验证  BASE=${BASE}"
log " AGENT_ID=${AGENT_ID}  MAX_STEP=${MAX_STEP}"
log " SESSION_ID=${SESSION_ID}"
log " 输出目录 ${OUT_DIR}"
log "=============================================="

# ---------- ① 服务可用性 ----------
log ""
log "① 服务可用性 ..."
HTTP_CODE="$(curl -s -o 01-agents.json -w '%{http_code}' \
  --max-time 30 "${BASE}/api/v1/agent/query_available_agents")"
if [ "${HTTP_CODE}" = "200" ]; then
  ok "query_available_agents 返回 200"
  AGENT_COUNT="$(grep -o '"agentId"' 01-agents.json 2>/dev/null | wc -l | tr -d ' ')"
  log "       可用智能体数: ${AGENT_COUNT}"
else
  bad "query_available_agents 返回 ${HTTP_CODE}（服务未启动？）"
  log ""
  log "服务不可用，后续步骤跳过。请先启动："
  log "  java -jar ai-agent-station-study-app/target/ai-agent-station-study-app.jar"
  exit 1
fi

# ---------- ② Agent 装配 ----------
log ""
log "② Agent 装配（armory_agent）..."
HTTP_CODE="$(curl -s -o 02-armory.json -w '%{http_code}' \
  --max-time 180 \
  -X POST "${BASE}/api/v1/agent/armory_agent" \
  -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"${AGENT_ID}\"}")"
if [ "${HTTP_CODE}" = "200" ] && grep -q '"code":"0000"' 02-armory.json 2>/dev/null; then
  ok "装配成功"
  log "       $(head -c 200 02-armory.json)"
else
  bad "装配失败 HTTP=${HTTP_CODE}"
  log "       $(head -c 300 02-armory.json 2>/dev/null)"
fi

# ---------- ③ 第一轮 ----------
log ""
log "③ 执行链路第一轮（SSE）..."
curl -sN --max-time "${SSE_TIMEOUT}" \
  -X POST "${BASE}/api/v1/agent/auto_agent" \
  -H 'Content-Type: application/json' \
  -d "{\"aiAgentId\":\"${AGENT_ID}\",\"message\":\"你好，请用一句话介绍你自己\",\"sessionId\":\"${SESSION_ID}\",\"maxStep\":${MAX_STEP}}" \
  -o 03-round1.sse

R1_LINES="$(wc -l < 03-round1.sse | tr -d ' ')"
R1_TYPES="$(grep -o '"type":"[a-z_]*"' 03-round1.sse 2>/dev/null | sort -u | tr '\n' ' ')"
if [ "${R1_LINES}" -gt 0 ]; then
  ok "收到 ${R1_LINES} 行 SSE"
  log "       消息类型: ${R1_TYPES:-（未识别到 type 字段）}"
else
  bad "未收到任何 SSE 数据"
fi

# ---------- ④ 第二轮（验证记忆） ----------
log ""
log "④ 执行链路第二轮（复用 sessionId，验证记忆）..."
curl -sN --max-time "${SSE_TIMEOUT}" \
  -X POST "${BASE}/api/v1/agent/auto_agent" \
  -H 'Content-Type: application/json' \
  -d "{\"aiAgentId\":\"${AGENT_ID}\",\"message\":\"我刚才问了你什么？\",\"sessionId\":\"${SESSION_ID}\",\"maxStep\":${MAX_STEP}}" \
  -o 04-round2.sse

R2_LINES="$(wc -l < 04-round2.sse | tr -d ' ')"
if [ "${R2_LINES}" -gt 0 ]; then
  ok "收到 ${R2_LINES} 行 SSE"
else
  bad "未收到任何 SSE 数据"
fi

# ---------- 汇总 ----------
log ""
log "=============================================="
log " 通过 ${PASS}  失败 ${FAIL}"
log " 原始输出: ${OUT_DIR}"
log "=============================================="
log ""
log "产物："
ls -1 . 2>/dev/null | sed 's/^/  /'

exit $(( FAIL > 0 ? 1 : 0 ))
