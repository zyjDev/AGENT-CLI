/**
 * 运行时验证：直接对 src/utils/auth.ts 的**真实函数体**做测试。
 *
 * 做法：读入真实源文件 → 剥掉 `import { Toast } from '@douyinfe/semi-ui'`
 * 这一行（Node 里加载不动 semi-ui）→ 在顶部注入 Toast / window / localStorage 桩
 * → 交给 Node 的 TS 类型剥离执行。
 *
 * ⚠️ 函数体**逐字节来自源文件**，本脚本不做任何改写，因此不存在「测的是副本」的问题。
 *
 * 运行：
 *   node docs/verify/auth-runtime-verify.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { pathToFileURL } from 'node:url';

const SRC = path.resolve('ai-agent-station-front/src/utils/auth.ts');
const raw = fs.readFileSync(SRC, 'utf8');

// 剥掉对 semi-ui 的 import（仅此一处），其余原样
const stripped = raw
  .split('\n')
  .filter((line) => !/^\s*import\s+.*@douyinfe\/semi-ui/.test(line))
  .join('\n');

const prelude = `
const __toasts = [];
const Toast = { error: (m) => { __toasts.push(m); }, success: () => {}, info: () => {} };
`;

const epilogue = `
export const __harness = { getToasts: () => __toasts };
`;

const tmp = path.join(os.tmpdir(), `auth-verify-${Date.now()}.ts`);
fs.writeFileSync(tmp, prelude + stripped + epilogue, 'utf8');

// ---- 桩：localStorage / window ----
const store = new Map();
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
  clear: () => store.clear(),
};

let replaced = null;
let baseFetchCalls = 0;
let baseFetchStatus = 200;

globalThis.window = {
  location: {
    pathname: '/rag-order-management',
    replace: (p) => { replaced = p; },
  },
  fetch: async () => {
    baseFetchCalls++;
    return { status: baseFetchStatus, ok: baseFetchStatus < 400, json: async () => ({ code: '0001' }) };
  },
};

const mod = await import(pathToFileURL(tmp).href);
const { isTokenExpired, isAuthenticated, clearAuthState, handleUnauthorized, installAuthFetchInterceptor } = mod;
const harness = mod.__harness;

// ---- 工具：造 JWT（不验签，只验 exp）----
const b64url = (obj) =>
  Buffer.from(JSON.stringify(obj)).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const makeJwt = (payload) => `eyJhbGciOiJIUzI1NiJ9.${b64url(payload)}.sig`;
const nowSec = () => Math.floor(Date.now() / 1000);

let pass = 0;
let fail = 0;
const check = (name, actual, expected) => {
  const ok = actual === expected;
  ok ? pass++ : fail++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}  (got ${JSON.stringify(actual)}, want ${JSON.stringify(expected)})`);
};

console.log('--- isTokenExpired ---');
check('未过期 token', isTokenExpired(makeJwt({ exp: nowSec() + 3600 })), false);
check('已过期 token', isTokenExpired(makeJwt({ exp: nowSec() - 1 })), true);
check('无 exp → 视为永不过期', isTokenExpired(makeJwt({ sub: 'admin' })), false);
check('乱码 token', isTokenExpired('not-a-jwt'), true);
check('空串', isTokenExpired(''), true);
check('段数不足', isTokenExpired('aaa.bbb'), true);
check('payload 非 JSON', isTokenExpired('h.%%%%.s'), true);
check('payload 含中文不炸（UTF-8 解码正确）', isTokenExpired(makeJwt({ exp: nowSec() + 60, name: '管理员张三' })), false);

console.log('\n--- isAuthenticated ---');
const setAll = (t) => {
  store.set('token', t);
  store.set('userInfo', JSON.stringify({ username: 'admin' }));
  store.set('isLoggedIn', 'true');
};
setAll(makeJwt({ exp: nowSec() + 3600 }));
check('三 key 齐全 + 未过期', isAuthenticated(), true);

setAll(makeJwt({ exp: nowSec() - 1 }));
check('★ 三 key 齐全但 token 已过期（旧守卫会放行 → 接口全 401）', isAuthenticated(), false);

store.clear(); setAll(makeJwt({ exp: nowSec() + 3600 })); store.delete('isLoggedIn');
check('缺 isLoggedIn', isAuthenticated(), false);

store.clear(); setAll(makeJwt({ exp: nowSec() + 3600 })); store.delete('userInfo');
check('缺 userInfo', isAuthenticated(), false);

console.log('\n--- clearAuthState ---');
store.clear(); setAll(makeJwt({ exp: nowSec() + 3600 }));
clearAuthState();
check('三个 key 全清', [store.has('token'), store.has('userInfo'), store.has('isLoggedIn')].join(','), 'false,false,false');

console.log('\n--- handleUnauthorized ---');
store.clear(); setAll(makeJwt({ exp: nowSec() - 1 }));
harness.getToasts().length = 0;
replaced = null;
handleUnauthorized();
check('清空登录态', store.size, 0);
check('提示文案准确（不是「请检查网络连接」）', harness.getToasts()[0], '登录已过期，请重新登录');
await new Promise((r) => setTimeout(r, 750));
check('跳转登录页', replaced, '/login');

/**
 * 造一个全新环境：新的 window（INSTALL_FLAG 挂在 window 上）+ 新的模块实例
 * （handlingUnauthorized 是模块级闭包变量）。否则上一段测试占住的锁会污染下一段。
 */
let envSeq = 0;
const freshEnv = async (locationStub) => {
  replaced = null;
  baseFetchStatus = 200;
  baseFetchCalls = 0;
  globalThis.window = {
    location: locationStub ?? { pathname: '/rag-order-management', replace: (p) => { replaced = p; } },
    fetch: async () => {
      baseFetchCalls++;
      return { status: baseFetchStatus, ok: baseFetchStatus < 400, json: async () => ({ code: '0001' }) };
    },
  };
  envSeq++;
  const m = await import(`${pathToFileURL(tmp).href}?env=${envSeq}`);
  return { mod: m, toasts: () => m.__harness.getToasts() };
};

console.log('\n--- installAuthFetchInterceptor ---');
const A = await freshEnv();
A.mod.installAuthFetchInterceptor();
const wrappedFetch = globalThis.window.fetch;
A.mod.installAuthFetchInterceptor(); // 幂等：不应再次包裹
check('二次安装幂等（引用不变）', globalThis.window.fetch === wrappedFetch, true);

store.clear(); setAll(makeJwt({ exp: nowSec() - 1 }));
baseFetchStatus = 401;
const resp = await wrappedFetch('http://127.0.0.1:8099/api/v1/admin/ai-client-rag-order/query-list', {});
check('401 响应体未被消费（调用方仍能读 status）', resp.status, 401);
check('401 清空登录态', store.size, 0);
check('401 给出准确提示', A.toasts()[0], '登录已过期，请重新登录');
await new Promise((r) => setTimeout(r, 750));
check('401 跳登录页', replaced, '/login');

// 非 401 不应误伤
const B = await freshEnv();
B.mod.installAuthFetchInterceptor();
store.clear(); setAll(makeJwt({ exp: nowSec() + 3600 }));
baseFetchStatus = 200;
await globalThis.window.fetch('http://127.0.0.1:8099/api/v1/admin/ai-client-rag-order/query-list', {});
check('200 不误伤（登录态保留 3 个 key）', store.size, 3);
check('200 不跳转', replaced, null);
check('200 不弹提示', B.toasts().length, 0);

console.log('\n--- 并发 401 去重 ---');
const C = await freshEnv();
C.mod.installAuthFetchInterceptor();
store.clear(); setAll(makeJwt({ exp: nowSec() - 1 }));
baseFetchStatus = 401;
await Promise.all([
  globalThis.window.fetch('a', {}),
  globalThis.window.fetch('b', {}),
  globalThis.window.fetch('c', {}),
]);
check('并发 3 个 401 只提示一次', C.toasts().length, 1);
check('并发 3 个 401 都真的发出去了', baseFetchCalls, 3);

console.log('\n--- 兜底：跳转被拦截时锁必须释放 ---');
const D = await freshEnv({ pathname: '/stuck-page', replace: () => { /* 模拟跳转被拦截 */ } });
store.clear(); setAll(makeJwt({ exp: nowSec() - 1 }));
D.mod.handleUnauthorized();
check('首次 401 仍会清登录态', store.size, 0);
await new Promise((r) => setTimeout(r, 3400));
store.clear(); setAll(makeJwt({ exp: nowSec() - 1 }));
D.mod.handleUnauthorized();
check('★ 3s 后锁已释放（否则后续 401 被永久静默吞掉）', store.size, 0);

console.log(`\n==== ${pass} passed, ${fail} failed ====`);
fs.unlinkSync(tmp);
process.exit(fail === 0 ? 0 : 1);
