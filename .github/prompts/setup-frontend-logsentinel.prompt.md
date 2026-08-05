---
mode: agent
description: >
  Inicializa el proyecto frontend de LogSentinel desde cero.
  Ejecutar UNA SOLA VEZ sobre el directorio frontend/ vacío.
  Instala y configura: Vite 6 + React 18 + TypeScript 5 + Tailwind CSS 4 +
  Vitest 2 + React Testing Library + MSW + Playwright.
---

# Setup Frontend LogSentinel

Inicializar el proyecto frontend completo siguiendo la arquitectura feature-driven
definida en `.github/copilot-instructions-frontend.md`.

Verificar que `frontend/` existe y está vacío antes de continuar.
Si `frontend/package.json` ya existe, detener y reportar al usuario.

## Paso 1 — Scaffold Vite + React + TypeScript

```bash
cd frontend
npm create vite@latest . -- --template react-ts
npm install
```

## Paso 2 — Instalar dependencias de producción

```bash
npm install tailwindcss @tailwindcss/vite
```

## Paso 3 — Instalar dependencias de desarrollo

```bash
npm install -D \
  vitest \
  @vitest/ui \
  jsdom \
  @testing-library/react \
  @testing-library/user-event \
  @testing-library/jest-dom \
  msw \
  @playwright/test \
  @types/node
```

## Paso 4 — Reemplazar `vite.config.ts`

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
});
```

## Paso 5 — Actualizar `tsconfig.json`

Agregar dentro de `compilerOptions`:
```json
{
  "compilerOptions": {
    "strict": true,
    "baseUrl": ".",
    "paths": { "@/*": ["src/*"] },
    "types": ["vitest/globals", "@testing-library/jest-dom"]
  }
}
```

## Paso 6 — Tema dark/console en Tailwind

Crear o reemplazar `src/index.css`:
```css
@import "tailwindcss";

:root {
  color-scheme: dark;
}

body {
  @apply bg-zinc-950 text-zinc-100 font-sans;
}
```

Crear `tailwind.config.ts` en la raíz de `frontend/`:
```typescript
import type { Config } from 'tailwindcss';

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
} satisfies Config;
```

## Paso 7 — Infraestructura base

Crear `src/test/setup.ts`:
```typescript
import '@testing-library/jest-dom';
```

Crear `src/contexts/UIContext.tsx`:
```typescript
import { createContext, useState, useContext, ReactNode } from 'react';

interface UIContextValue {
  isDark: boolean;
  toggleTheme: () => void;
}

const UIContext = createContext<UIContextValue | null>(null);

export function UIProvider({ children }: { children: ReactNode }) {
  const [isDark, setIsDark] = useState(true);
  return (
    <UIContext.Provider value={{ isDark, toggleTheme: () => setIsDark(p => !p) }}>
      {children}
    </UIContext.Provider>
  );
}

export function useUI() {
  const ctx = useContext(UIContext);
  if (!ctx) throw new Error('useUI debe usarse dentro de UIProvider');
  return ctx;
}
```

Crear `src/providers/AppProvider.tsx`:
```typescript
import { ReactNode } from 'react';
import { UIProvider } from '@/contexts/UIContext';

export function AppProvider({ children }: { children: ReactNode }) {
  return <UIProvider>{children}</UIProvider>;
}
```

Reemplazar `src/main.tsx`:
```typescript
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AppProvider } from '@/providers/AppProvider';
import '@/index.css';
import App from '@/App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProvider>
      <App />
    </AppProvider>
  </StrictMode>,
);
```

Reemplazar `src/App.tsx` con placeholder mínimo:
```typescript
export default function App() {
  return (
    <main className="min-h-screen bg-zinc-950 text-zinc-100 flex items-center justify-center">
      <h1 className="text-2xl font-mono text-green-400">LogSentinel</h1>
    </main>
  );
}
```

## Paso 8 — Variables de entorno

Crear `frontend/.env.example`:
```
# Copiar a .env.local y completar
VITE_API_BASE_URL=http://localhost:8080
```

Crear `frontend/.env.local`:
```
VITE_API_BASE_URL=http://localhost:8080
```

Verificar que `frontend/.gitignore` contiene `.env.local`.
Si no existe, agregar la línea.

## Paso 9 — Configurar Playwright

```bash
npx playwright install --with-deps chromium
```

Crear `frontend/playwright.config.ts`:
```typescript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  use: { baseURL: 'http://localhost:5173' },
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
});
```

Crear directorio `frontend/e2e/.gitkeep` (placeholder vacío).

## Verificación final

Ejecutar los tres comandos en orden y confirmar que todos terminan sin errores:

```bash
cd frontend
npm run build        # TypeScript limpio + bundle de producción
npm test -- --run    # Vitest — 0 tests, 0 failed (no hay tests aún)
npx playwright test  # Playwright — 0 tests (no hay e2e aún)
```

Si `npm run build` falla con error de path alias: ya debería estar resuelto con `@types/node`.
Si persiste, verificar que `tsconfig.node.json` también tiene `paths` configurado.

Reportar al usuario:
- Versiones instaladas (React, TypeScript, Vite, Tailwind)
- Estructura de directorios creada
- Resultado de cada comando de verificación
