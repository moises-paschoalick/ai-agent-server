/// <reference types="vitest/config" />
import path from 'path';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// The browser only talks to /api on this origin. The dev server proxies it to the backend and adds the
// API key there, so the key never reaches the browser. BACKEND_URL and AGENT_API_KEY come from the
// environment or the monorepo's root .env; they are not VITE_* variables, so Vite never exposes them.
export default defineConfig(({ mode }) => {
  const env = { ...loadEnv(mode, path.resolve(__dirname, '..'), ''), ...process.env };
  const backendUrl = env.BACKEND_URL || 'http://localhost:8080';
  const apiKey = env.AGENT_API_KEY || '';

  return {
    server: {
      port: 3000,
      host: '0.0.0.0',
      proxy: {
        // http-proxy pipes the response as it comes, so streamed answers (text/event-stream) are not held
        '/api': {
          target: backendUrl,
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/api/, ''),
          headers: apiKey ? { Authorization: `Bearer ${apiKey}` } : {},
        },
      },
    },
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      },
    },
    test: {
      environment: 'node',
    },
  };
});
