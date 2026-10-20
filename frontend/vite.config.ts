import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    host: true, // нужно для доступа извне контейнера (0.0.0.0)
    port: 5173,
    watch: {
      usePolling: true, // Включаем опрос файлов для Docker на Windows
      interval: 100,
    },
    proxy: { // Временно для связи с backend
      '/rest-api': {
        target: 'http://smartcast-backend-dev:8080',
        changeOrigin: true,
      }
    }
  },
})
