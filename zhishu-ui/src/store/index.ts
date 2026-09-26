import { createPinia } from 'pinia'
import type { App } from 'vue'

export const pinia = createPinia()

export function setupStore(app: App): void {
  app.use(pinia)
}
