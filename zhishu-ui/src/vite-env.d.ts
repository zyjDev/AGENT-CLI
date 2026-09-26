/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 请求前缀：开发环境走 Vite 代理（/api），生产环境按部署方式调整 */
  readonly VITE_API_BASE: string
  /** 应用标题（中文名：智枢） */
  readonly VITE_APP_TITLE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
