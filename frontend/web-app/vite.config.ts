import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true, // Fail if port is already in use
    allowedHosts: true, // Allow ngrok domains
    proxy: {
      '/api/v1/coupons': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/api/v1/categories': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
