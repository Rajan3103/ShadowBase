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
      // "/connect/..." goes to Kafka Connect (Debezium) on port 8083, without "/connect":
      // /connect/connectors/x/status → http://localhost:8083/connectors/x/status
      '/connect': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/connect/, ''),
      },
    },
  },
})
