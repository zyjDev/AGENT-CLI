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
    host: '0.0.0.0',
    allowedHosts: true,
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
