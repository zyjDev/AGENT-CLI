import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 后端服务地址。
 * 与后端 WebCorsConfig 的 allowed-origins 默认白名单保持一致（8099），本工程不改动后端。
 */
const BACKEND_TARGET = 'http://127.0.0.1:8099'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 只监听本机（P2-20）。曾用 '0.0.0.0' + allowedHosts: true，等于把 dev server 变成
    // 「开放代理」：同网络下任何人访问 http://<本机IP>:5173/api/... 都能借它打通后端，
    // 且后端因 changeOrigin 只看到同源请求，绕过 CORS 白名单。
    // 确需局域网/手机调试时，临时用 `npm run dev -- --host` 开启，不要写死在这里。
    host: 'localhost',
    allowedHosts: [],
    // 固定 5173：该端口已在后端 CORS 白名单内，直连后端不会跨域
    port: 5173,
    strictPort: true,
    proxy: {
      // 走代理时请求同源，连白名单都不需要
      '/api': {
        target: BACKEND_TARGET,
        changeOrigin: true,
      },
    },
  },
  build: {
    // LogicFlow / echarts 体积较大，放宽告警阈值避免噪音
    chunkSizeWarningLimit: 1600,
  },
})
