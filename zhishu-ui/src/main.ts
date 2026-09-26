import { createApp } from 'vue'
import Antd from 'ant-design-vue'

// 顺序重要：先 antd 的 reset（统一浏览器默认样式），再引入本工程的 Tailwind 与设计令牌，
// 保证 body 字体、背景等全局令牌由本工程说了算（antd 组件样式自带 .ant-* 类，不受影响）
import 'ant-design-vue/dist/reset.css'
import './index.css'

import App from './App.vue'
import { setupStore } from './store'
import { router, setupRouter } from './router'
import { installFetchUnauthorizedInterceptor, setUnauthorizedHandler } from './utils/auth'

const app = createApp(App)

setupStore(app)
setupRouter(app)
app.use(Antd)

// 401 时用路由跳转而不是整页刷新：保住 SPA 体验，同时复用同一个登录态清理逻辑
setUnauthorizedHandler(() => {
  void router.replace({ path: '/login' })
})
installFetchUnauthorizedInterceptor()

app.mount('#app')
