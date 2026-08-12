import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { resolve } from 'path';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': resolve(import.meta.dirname, './src') },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Excluye explícitamente `e2e/` (specs de Playwright, otro test runner)
    // del descubrimiento de tests de Vitest.
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
  },
});
