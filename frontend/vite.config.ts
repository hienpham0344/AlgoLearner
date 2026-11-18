import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';
import { authDemoPlugin } from './dev/authDemoPlugin';
import { profileDemoPlugin } from './dev/profileDemoPlugin';
import { dashboardDemoPlugin } from './dev/dashboardDemoPlugin';
import { lessonsDemoPlugin } from './dev/lessonsDemoPlugin';

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  cacheDir: `node_modules/.vite-${mode}`,
  plugins: [react(), tailwindcss(), ...(['auth-demo', 'lessons-demo'].includes(mode) ? [lessonsDemoPlugin(), profileDemoPlugin(), dashboardDemoPlugin()] : []), ...(['auth-demo', 'backend-mock', 'lessons-demo'].includes(mode) ? [authDemoPlugin(mode === 'auth-demo' ? 'demo' : 'backend-mock')] : [])],
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
