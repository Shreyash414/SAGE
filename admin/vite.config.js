import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// SAGE Admin Panel Vite Configuration (Port 5174)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    strictPort: true,
    host: '0.0.0.0',
  },
  preview: {
    port: 5174,
    strictPort: true,
    host: '0.0.0.0',
  },
})
