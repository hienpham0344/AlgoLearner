import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';
import { backendFirstPlugin, offlineNotFoundPlugin } from './dev/backendFirstPlugin';
import { authDemoPlugin } from './dev/authDemoPlugin';
import { profileDemoPlugin } from './dev/profileDemoPlugin';
import { dashboardDemoPlugin } from './dev/dashboardDemoPlugin';
import { lessonsDemoPlugin } from './dev/lessonsDemoPlugin';

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  cacheDir: `node_modules/.vite-${mode}`,
  plugins: [
    react(),
    tailwindcss(),
    ...(mode === 'lessons-demo' ? [lessonsDemoPlugin()] : []),
    backendFirstPlugin(process.env.ALGOLEARN_API_TARGET || (mode === 'lessons-demo' ? 'http://127.0.0.1:19999' : 'http://127.0.0.1:8080')),
    authDemoPlugin(mode === 'auth-demo' ? 'demo' : 'backend-mock'),
    profileDemoPlugin(),
    dashboardDemoPlugin(),
    offlineNotFoundPlugin(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
  },
}));
