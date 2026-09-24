import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    port: 5173,
    // Same-origin in development too, so the refresh cookie and CSRF cookie behave as in production.
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator/health': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Each worker holds a whole jsdom; on a small machine (this one has 6 GB shared with Docker and
    // an IDE) unlimited parallelism runs out of memory.
    maxWorkers: 2,
  },
})
