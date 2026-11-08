import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';
import { authDemoPlugin } from './dev/authDemoPlugin';
import { profileDemoPlugin } from './dev/profileDemoPlugin';
import { dashboardDemoPlugin } from './dev/dashboardDemoPlugin';

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react(), tailwindcss(), ...(['auth-demo', 'backend-mock'].includes(mode) ? [authDemoPlugin(mode === 'backend-mock' ? 'backend-mock' : 'demo')] : []), ...(mode === 'auth-demo' ? [profileDemoPlugin(), dashboardDemoPlugin()] : [])],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: process.env.ALGOLEARN_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}));
