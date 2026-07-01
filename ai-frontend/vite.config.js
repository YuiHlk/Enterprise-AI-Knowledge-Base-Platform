import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          // Disable response buffering for SSE streaming endpoints
          proxy.on('proxyReq', (proxyReq, req) => {
            if (req.url.includes('/ask/stream')) {
              proxyReq.setHeader('Connection', 'keep-alive')
              proxyReq.setHeader('Cache-Control', 'no-cache')
            }
          })
          proxy.on('proxyRes', (proxyRes, req) => {
            if (req.url.includes('/ask/stream')) {
              // Remove any content-length to allow chunked transfer
              delete proxyRes.headers['content-length']
            }
          })
        }
      }
    }
  }
})
