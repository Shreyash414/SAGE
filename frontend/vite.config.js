import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// SAGE User Portal Vite Configuration (Port 5173)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    host: '0.0.0.0',
  },
  preview: {
    port: 5173,
    strictPort: true,
    host: '0.0.0.0',
  },
})

