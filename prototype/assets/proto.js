/* ============================================================
   AI Agent Station · 原型交互脚本（仅演示用）
   ============================================================ */

/** 页面跳转 */
function go(url) {
  window.location.href = url;
}

/** 轻提示 */
function protoToast(text) {
  const el = document.createElement('div');
  el.className = 'fixed left-1/2 top-6 z-[100] -translate-x-1/2 rounded-lg bg-[#1F2430] px-4 py-2 text-[13px] text-white shadow-lg';
  el.style.animation = 'fadeUp .2s ease both';
  el.textContent = text;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 2200);
}

/** 密码明文切换 */
function togglePassword(inputId, btn) {
  const input = document.getElementById(inputId);
  if (!input) return;
  const show = input.type === 'password';
  input.type = show ? 'text' : 'password';
  btn.dataset.show = show ? '1' : '0';
  btn.innerHTML = show ? btn.dataset.hide : btn.dataset.see;
}

/** 模拟登录：loading -> 跳转用户端 */
function doLogin(btn) {
  const username = document.getElementById('username');
  const password = document.getElementById('password');
  if (!username || !username.value.trim() || !password || !password.value.trim()) {
    protoToast('请输入账号和密码');
    return;
  }
  btn.disabled = true;
  btn.innerHTML = '<span class="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-white/40 border-t-white align-[-2px]"></span> 正在登录...';
  setTimeout(() => go('chat.html'), 900);
}

/** 快捷登录 */
function quickLogin(btn) {
  document.getElementById('username').value = 'admin';
  document.getElementById('password').value = '123456';
  doLogin(btn);
}

/** 弹窗开关 */
function openMask(id) {
  const el = document.getElementById(id);
  if (el) el.classList.add('show');
}
function closeMask(id) {
  const el = document.getElementById(id);
  if (el) el.classList.remove('show');
}

/** 侧栏折叠 */
function toggleCollapse(sidebarId, mainId) {
  const sb = document.getElementById(sidebarId);
  const main = document.getElementById(mainId);
  if (!sb || !main) return;
  const collapsed = sb.classList.toggle('w-0');
  sb.classList.toggle('w-60', !collapsed);
  sb.classList.toggle('overflow-hidden', collapsed);
  main.classList.toggle('ml-60', !collapsed);
  main.classList.toggle('ml-0', collapsed);
}

/** 画布节点选中：切换节点高亮 + 同步右侧属性抽屉标题 */
function selectNode(nodeEl, type, title, subtitle) {
  document.querySelectorAll('.node-card').forEach((n) => n.classList.remove('is-active'));
  nodeEl.classList.add('is-active');
  const t = document.getElementById('drawerType');
  const n = document.getElementById('drawerTitle');
  const s = document.getElementById('drawerSub');
  if (t) t.textContent = type;
  if (n) n.value = title;
  if (s) s.textContent = subtitle;
  const drawer = document.getElementById('drawer');
  if (drawer) drawer.classList.remove('translate-x-full');
}

/** 开关式按钮的选中态 */
function toggleBtn(btn) {
  btn.classList.toggle('!bg-[#EEF0FE]');
  btn.classList.toggle('!text-[#4F46E5]');
  btn.classList.toggle('!border-[#C7CBF7]');
}
