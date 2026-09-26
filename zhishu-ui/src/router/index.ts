import { createRouter, createWebHistory } from 'vue-router'
import type { App } from 'vue'
import { routes } from './routes'
import { setupRouterGuard } from './guard'

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

export function setupRouter(app: App): void {
  setupRouterGuard(router)
  app.use(router)
}

export { router }
