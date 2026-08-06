---
applyTo: frontend/**
---

# Instrucciones: Frontend LogSentinel

## Stack exacto
React 19.x · TypeScript 6.x (strict) · Vite 8 · Tailwind CSS 4 · Vitest 4 · Playwright 1.62+

## Gestión de estado — regla no negociable
SIN Zustand, Redux ni MobX. Solo Context API + useReducer nativos de React.

| Nivel | Tecnología | Caso de uso |
|---|---|---|
| Global (toda la app) | `useState` en `UIContext` | Tema dark/light, estado del sidebar |
| Feature (scoped) | `useReducer` + `Context` en `{Name}Context.tsx` | Pipeline del incidente, estado del runbook |
| Local (componente) | `useState` | Dropdown abierto, modal visible |

Los componentes **NUNCA** importan `useContext` directamente — siempre via hook personalizado:

```typescript
// ✅ Correcto — el componente solo pide datos
const { state, dispatch } = useIncident();

// ❌ Incorrecto — acopla el componente a la implementación interna
const context = useContext(IncidentContext);
```

El hook lanza `Error` si se usa fuera del Provider.

## APIs y comunicación — patrones obligatorios

```typescript
// Sin Axios. URL base siempre desde VITE_API_BASE_URL.
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

// REST — manejo explícito de errores HTTP
const res = await fetch(`${API_BASE}/api/v1/incidents`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(payload),
});
if (!res.ok) throw new Error(`HTTP ${res.status}`);

// SSE — cleanup OBLIGATORIO en return del useEffect
useEffect(() => {
  const source = new EventSource(`${API_BASE}/api/v1/incidents/${id}/stream`);
  source.onmessage = (e) => dispatch({ type: 'RECEIVE_CHUNK', payload: e.data });
  source.onerror  = ()  => source.close();
  return () => source.close(); // NUNCA omitir — leak garantizado en StrictMode
}, [id]);
```

## SSE — reglas de seguridad
- El texto recibido del stream se renderiza como contenido JSX de React (escaped automáticamente)
- **NUNCA** usar `dangerouslySetInnerHTML` con datos del stream — los logs son untrusted input
- Los chunks se acumulan en `diagnosticBuffer` del reducer; nunca se evalúan como código

## Tailwind CSS — tema dark/console obligatorio
Estrategia dark mode: `class` (en `tailwind.config.ts`).

```
Fondo:          bg-zinc-950 / bg-zinc-900
Texto primario: text-zinc-100
Terminal (logs): text-green-400 font-mono text-sm
Bordes:         border-zinc-700
Acento warning: text-amber-400
Acento error:   text-red-400
Acento info:    text-blue-400
```

Prohibido: valores arbitrarios `px-[13px]`, raw hex en `style={}`, inline styles, `!important`.
Espaciado: escala estándar `p-2 / p-4 / p-6 / p-8` — no `p-3.5`.

## TypeScript 6 — reglas

### `verbatimModuleSyntax: true` — crítico en TS 6
TypeScript 6 activa `verbatimModuleSyntax` por defecto. Los tipos DEBEN importarse con `import type`:

```typescript
// ✅ Correcto en TS 6
import { useState, useContext } from 'react';
import type { ReactNode } from 'react';

// ❌ Incorrecto — falla en build con verbatimModuleSyntax
import { useState, useContext, ReactNode } from 'react';
```

Regla: si el símbolo solo se usa como tipo (en anotaciones `: Tipo` o `<Tipo>`), usar `import type`.

- `strict: true` en tsconfig — **nunca** `any` ni `as unknown as T` para esquivar el compilador
- Props: interfaces con sufijo `Props` (`LogTerminalProps`, `ScenarioSelectorProps`)
- Acciones del reducer: discriminated unions tipadas explícitas
- Imports: alias `@/` → `src/` — **nunca** rutas relativas `../../`
- Tipos de dominio en `src/features/{name}/types/{name}.types.ts`

## Estructura de features — obligatoria
```
src/features/{name}/
├── api/{name}Api.ts          ← fetch nativo + EventSource
├── components/               ← componentes visuales sin estado propio
├── context/{Name}Context.tsx ← useReducer + acciones tipadas + Provider
├── hooks/use{Name}.ts        ← ÚNICO punto de acceso al contexto
├── types/{name}.types.ts     ← interfaces TypeScript de la feature
└── index.ts                  ← barrel export público
```

## Tests — stack obligatorio
- **Reducers:** Vitest puro — sin DOM, sin render
- **Hooks:** Vitest + `renderHook` de @testing-library/react
- **Componentes:** Vitest + React Testing Library (`screen`, `userEvent`)
- **API y SSE:** MSW (Mock Service Worker) — intercepta fetch y EventSource
- **E2E:** Playwright
- Comandos: `npm test` (Vitest) · `npx playwright test` (E2E)

## DevSecOps — reglas no negociables
- Credenciales **NUNCA** en código fuente — siempre `VITE_*` en `.env.local`
- `.env.local` en `.gitignore` — solo `.env.example` va al repositorio
- No exponer mensajes de error técnicos del backend en la UI — mensajes genéricos al usuario
- Validar inputs antes de enviar al backend (longitud mínima del log, campos requeridos)
- Nuevas variables de entorno frontend: documentarlas en la skill `provision-logsentinel-env`
