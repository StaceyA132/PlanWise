import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Forward /api/* to the Spring Boot backend. The browser only ever talks to the
    // Vite dev server, so there's no cross-origin request and no CORS setup needed.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
