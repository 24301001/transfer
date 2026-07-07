import { createPinia } from 'pinia'

export function setupStore(app) {
  const pinia = createPinia()
  app.use(pinia)
}
