import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // sockjs-client references the Node global `global`; map it to the browser global.
  define: {
    global: 'globalThis',
  },
})
