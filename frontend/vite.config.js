// vite.config.js — settings for the Vite dev server and build.
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Any request starting with "/api" is forwarded to the Spring Boot backend.
    // The browser thinks it's talking to Vite (same address), so there are no CORS problems.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
