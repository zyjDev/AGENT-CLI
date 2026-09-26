import type { Router } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { LOGIN_PATH } from '@/utils/auth'

const APP_TITLE = import.meta.env.VITE_APP_TITLE || '智枢'

/**
 * 全局登录守卫。
 * - 未登录访问受保护页 → 跳登录页，并带上 redirect 以便登录后回到原页；
 * - 已登录访问登录页 → 直接落到默认页 /chat（不再二次输入密码）。
 */
export function setupRouterGuard(router: Router): void {
  router.beforeEach((to) => {
    const userStore = useUserStore()
    const isPublic = Boolean(to.meta.public)

    if (!userStore.isAuthenticated && !isPublic) {
      return {
        path: LOGIN_PATH,
        query: to.fullPath === '/' ? undefined : { redirect: to.fullPath },
        replace: true,
      }
    }

    // 已登录还去登录页：直接回默认落地页
    if (userStore.isAuthenticated && to.path === LOGIN_PATH) {
      return { path: '/chat', replace: true }
    }

    return true
  })

  router.afterEach((to) => {
    const title = to.meta.title as string | undefined
    document.title = title ? `${title} · ${APP_TITLE}` : APP_TITLE
  })
}
