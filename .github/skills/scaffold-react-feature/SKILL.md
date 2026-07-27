---
name: scaffold-react-feature
description: >
  Generates feature-driven React 18+ module following LogSentinel's architecture.
  Use when: "crear feature React X", "scaffold feature incidents/runbooks",
  "inicializar frontend feature", "scaffold React incidents", "scaffold React runbooks".
---

# Skill: scaffold-react-feature

## Propósito
Genera la estructura feature-driven de React 18+ siguiendo la arquitectura de LogSentinel.
Usar cuando: "crear feature React X", "scaffold feature incidents/runbooks",
"inicializar frontend feature".

## Estructura a generar

```
src/features/{name}/
├── api/{name}Api.ts           ← fetch nativo + EventSource para SSE
├── components/                ← componentes visuales sin estado propio
├── context/{Name}Context.tsx  ← useReducer + acciones tipadas
├── hooks/use{Name}.ts         ← abstracción del contexto (punto de acceso único)
├── types/{name}.types.ts      ← interfaces TypeScript
└── index.ts                   ← barrel export público
```

## Templates

### `context/{Name}Context.tsx`
```typescript
import { createContext, useReducer, ReactNode } from 'react';

interface {Name}State {
  status: 'idle' | 'analyzing' | 'resolved' | 'error';
  diagnosticBuffer: string;
  errorMessage: string | null;
}

type {Name}Action =
  | { type: 'START_ANALYSIS' }
  | { type: 'RECEIVE_CHUNK'; payload: string }
  | { type: 'ANALYSIS_COMPLETE' }
  | { type: 'SET_ERROR'; payload: string }
  | { type: 'RESET' };

const initialState: {Name}State = {
  status: 'idle',
  diagnosticBuffer: '',
  errorMessage: null,
};

function {name}Reducer(state: {Name}State, action: {Name}Action): {Name}State {
  switch (action.type) {
    case 'START_ANALYSIS':    return { ...state, status: 'analyzing', diagnosticBuffer: '' };
    case 'RECEIVE_CHUNK':     return { ...state, diagnosticBuffer: state.diagnosticBuffer + action.payload };
    case 'ANALYSIS_COMPLETE': return { ...state, status: 'resolved' };
    case 'SET_ERROR':         return { ...state, status: 'error', errorMessage: action.payload };
    case 'RESET':             return initialState;
    default:                  return state;
  }
}

type {Name}ContextValue = {
  state: {Name}State;
  dispatch: React.Dispatch<{Name}Action>;
};

const {Name}Context = createContext<{Name}ContextValue | null>(null);

export function {Name}Provider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer({name}Reducer, initialState);
  return (
    <{Name}Context.Provider value={{ state, dispatch }}>
      {children}
    </{Name}Context.Provider>
  );
}

export { {Name}Context };
```

### `hooks/use{Name}.ts`
```typescript
import { useContext } from 'react';
import { {Name}Context } from '../context/{Name}Context';

// Los componentes NUNCA importan useContext directamente.
// Este hook es el único punto de acceso al contexto de la feature.
export function use{Name}() {
  const context = useContext({Name}Context);
  if (!context) {
    throw new Error('use{Name} debe usarse dentro de {Name}Provider');
  }
  return context;
}
```

### `api/{name}Api.ts`
```typescript
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export async function create{Name}(request: {Name}Request): Promise<{Name}Response> {
  const response = await fetch(`${API_BASE}/api/v1/{resource}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

// SSE para streaming de diagnóstico (solo para endpoint de analysis)
export function streamDiagnosis(
  id: string,
  onChunk: (chunk: string) => void,
): () => void {
  const source = new EventSource(`${API_BASE}/api/v1/incidents/${id}/stream`);
  source.onmessage = (e) => onChunk(JSON.parse(e.data).chunk ?? e.data);
  source.onerror = () => source.close();
  return () => source.close(); // retorna cleanup para useEffect
}
```

### `index.ts` (barrel export)
```typescript
export { {Name}Provider } from './context/{Name}Context';
export { use{Name} } from './hooks/use{Name}';
export type { } from './types/{name}.types';
// Exportar componentes públicos de la feature
```

## Reglas críticas
- SIN Zustand, Redux ni MobX — solo Context API + useReducer
- Los componentes NUNCA usan `useContext` directamente — siempre via hook
- La URL base SIEMPRE desde `VITE_API_BASE_URL` (nunca hardcoded)
- Las acciones del reducer son discriminated unions tipadas
- El hook lanza `Error` si se usa fuera del Provider
- El cleanup de `EventSource` se retorna para usarlo en `useEffect`
