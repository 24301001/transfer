import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import fs from 'fs'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    host: true,
    https: {
      key: fs.readFileSync(resolve(__dirname, 'key.pem')),
      cert: fs.readFileSync(resolve(__dirname, 'cert.pem')),
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/runs': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
