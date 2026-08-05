---
mode: agent
description: >
  Implementa el componente LogTerminal y el hook useSSEStream para la feature incidents.
  Ejecutar UNA VEZ, después de haber scaffoldeado la feature incidents con
  scaffold-react-feature. Es el componente más crítico del frontend: conecta con
  el endpoint SSE del backend y renderiza el diagnóstico de IA token a token.
---

# Implementar LogTerminal + useSSEStream

## Prerequisitos

Antes de empezar verificar:
- [ ] `src/features/incidents/` existe (generado por `scaffold-react-feature`)
- [ ] `IncidentContext.tsx` tiene los action types: `START_ANALYSIS`, `RECEIVE_CHUNK`, `STREAM_COMPLETE`, `STREAM_ERROR`, `RESET`
- [ ] `useIncident()` hook existe en `src/features/incidents/hooks/useIncident.ts`
- [ ] `npm test` pasa sin errores

Si falta algún prerequisito, usar `scaffold-react-feature` primero.

## Contexto del endpoint SSE

El backend expone: `GET /api/v1/incidents/{id}/stream`
- `Content-Type: text/event-stream`
- Cada evento: `data: {"chunk": "texto parcial"}\n\n`
- Evento de fin: `event: complete\ndata: {}\n\n`

## Paso 1 — Actualizar IncidentContext con los action types SSE

Verificar que `IncidentContext.tsx` tiene exactamente estos types en la union.
Si alguno falta, agregarlo al tipo de acción y al switch del reducer:

```typescript
// Tipos de acción necesarios para SSE
type IncidentAction =
  | { type: 'START_ANALYSIS' }
  | { type: 'RECEIVE_CHUNK'; payload: string }
  | { type: 'STREAM_COMPLETE' }
  | { type: 'STREAM_ERROR'; payload: string }
  | { type: 'RESET' };

// Estado necesario
interface IncidentState {
  status: 'idle' | 'analyzing' | 'streaming' | 'resolved' | 'error';
  diagnosticBuffer: string;
  errorMessage: string | null;
}

// Cases del reducer para SSE
case 'RECEIVE_CHUNK':
  return { ...state, status: 'streaming', diagnosticBuffer: state.diagnosticBuffer + action.payload };
case 'STREAM_COMPLETE':
  return { ...state, status: 'resolved' };
case 'STREAM_ERROR':
  return { ...state, status: 'error', errorMessage: action.payload };
```

## Paso 2 — Crear `src/features/incidents/hooks/useSSEStream.ts`

```typescript
import { useEffect, useRef } from 'react';
import { useIncident } from './useIncident';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

const MAX_RETRIES = 3;
const RETRY_BASE_MS = 1000;

export function useSSEStream(incidentId: string | null) {
  const { dispatch } = useIncident();
  const retriesRef = useRef(0);

  useEffect(() => {
    if (!incidentId) return;

    function connect() {
      const source = new EventSource(
        `${API_BASE}/api/v1/incidents/${incidentId}/stream`,
      );

      source.onmessage = (e) => {
        retriesRef.current = 0;
        try {
          const parsed = JSON.parse(e.data) as { chunk?: string };
          dispatch({ type: 'RECEIVE_CHUNK', payload: parsed.chunk ?? e.data });
        } catch {
          // el backend envió texto plano, no JSON
          dispatch({ type: 'RECEIVE_CHUNK', payload: e.data });
        }
      };

      // evento custom que el backend envía al finalizar el stream
      source.addEventListener('complete', () => {
        dispatch({ type: 'STREAM_COMPLETE' });
        source.close();
      });

      source.onerror = () => {
        source.close();
        if (retriesRef.current < MAX_RETRIES) {
          const delay = RETRY_BASE_MS * 2 ** retriesRef.current;
          retriesRef.current += 1;
          setTimeout(connect, delay);
        } else {
          dispatch({
            type: 'STREAM_ERROR',
            payload: 'Conexión perdida. Verifica que el servidor esté disponible.',
          });
        }
      };

      return source;
    }

    const source = connect();
    return () => {
      source.close();
      retriesRef.current = 0;
    };
  }, [incidentId, dispatch]);
}
```

## Paso 3 — Crear `src/features/incidents/components/LogTerminal.tsx`

```typescript
import { useEffect, useRef } from 'react';
import { useIncident } from '../hooks/useIncident';
import { useSSEStream } from '../hooks/useSSEStream';

interface LogTerminalProps {
  incidentId: string | null;
}

const STATUS_LABEL: Record<string, string> = {
  idle:      'Esperando análisis...',
  analyzing: 'Conectando con el agente SRE...',
  streaming: 'Analizando incidente...',
  resolved:  'Diagnóstico completado',
  error:     'Error en la conexión',
};

export function LogTerminal({ incidentId }: LogTerminalProps) {
  const { state } = useIncident();
  const bottomRef = useRef<HTMLDivElement>(null);

  // Solo conectar SSE cuando el análisis está activo
  useSSEStream(state.status === 'analyzing' ? incidentId : null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [state.diagnosticBuffer]);

  return (
    <section
      aria-label="Terminal de diagnóstico"
      aria-live="polite"
      aria-busy={state.status === 'streaming'}
      className="flex flex-col h-full bg-zinc-950 border border-zinc-700 rounded-lg overflow-hidden"
    >
      <div className="flex items-center gap-2 px-4 py-2 bg-zinc-900 border-b border-zinc-700">
        <span
          className={`h-2 w-2 rounded-full ${
            state.status === 'streaming' ? 'bg-green-400 animate-pulse' :
            state.status === 'error'     ? 'bg-red-400'                 :
            state.status === 'resolved'  ? 'bg-blue-400'                : 'bg-zinc-500'
          }`}
        />
        <span className="text-xs text-zinc-400 font-mono">
          {STATUS_LABEL[state.status] ?? 'Listo'}
        </span>
      </div>

      <div className="flex-1 overflow-y-auto p-4 font-mono text-sm text-green-400 whitespace-pre-wrap">
        {state.diagnosticBuffer || (
          <span className="text-zinc-600">
            El diagnóstico aparecerá aquí token a token...
          </span>
        )}
        <div ref={bottomRef} />
      </div>

      {state.status === 'error' && state.errorMessage && (
        <div
          role="alert"
          className="px-4 py-2 bg-red-950 border-t border-red-800 text-red-400 text-xs font-mono"
        >
          {state.errorMessage}
        </div>
      )}
    </section>
  );
}
```

## Paso 4 — Crear `src/features/incidents/components/LogTerminal.test.tsx`

```typescript
import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { IncidentProvider } from '../context/IncidentContext';
import { LogTerminal } from './LogTerminal';

// MSW no soporta EventSource; mock manual del constructor
const mockClose = vi.fn();
const mockSource = {
  onmessage:        null as ((e: MessageEvent) => void) | null,
  onerror:          null as ((e: Event) => void) | null,
  addEventListener: vi.fn(),
  close:            mockClose,
};

vi.stubGlobal('EventSource', vi.fn(() => mockSource));

function renderWithProvider(incidentId: string | null) {
  return render(
    <IncidentProvider>
      <LogTerminal incidentId={incidentId} />
    </IncidentProvider>,
  );
}

describe('LogTerminal', () => {
  beforeEach(() => {
    mockClose.mockClear();
    vi.mocked(EventSource).mockClear();
    mockSource.onmessage = null;
    mockSource.onerror = null;
  });

  it('muestra placeholder cuando no hay diagnóstico', () => {
    renderWithProvider(null);
    expect(screen.getByText(/El diagnóstico aparecerá aquí/i)).toBeInTheDocument();
  });

  it('no abre EventSource si incidentId es null', () => {
    renderWithProvider(null);
    expect(EventSource).not.toHaveBeenCalled();
  });

  it('cierra EventSource al desmontar — sin leak', () => {
    const { unmount } = renderWithProvider('incident-123');
    unmount();
    expect(mockClose).toHaveBeenCalledTimes(1);
  });

  it('renderiza chunks recibidos progresivamente', async () => {
    renderWithProvider('incident-123');

    mockSource.onmessage?.({
      data: JSON.stringify({ chunk: 'RootCause: timeout en conexión DB' }),
    } as MessageEvent);

    await waitFor(() => {
      expect(
        screen.getByText(/RootCause: timeout en conexión DB/i),
      ).toBeInTheDocument();
    });
  });

  it('muestra banner de error cuando el stream falla', async () => {
    renderWithProvider('incident-123');
    // Agotar los reintentos simulando errores repetidos
    for (let i = 0; i <= 3; i++) {
      mockSource.onerror?.(new Event('error'));
    }

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
    });
  });
});
```

## Paso 5 — Exportar desde el barrel

Agregar al `src/features/incidents/index.ts`:
```typescript
export { LogTerminal } from './components/LogTerminal';
export { useSSEStream } from './hooks/useSSEStream';
```

## Verificación

```bash
cd frontend
npm test -- LogTerminal    # Todos los tests del componente deben pasar
npm run build              # Sin errores TypeScript
```

Confirmar manualmente:
- [ ] El mock de `EventSource.close` se llama exactamente 1 vez al unmount
- [ ] No hay warnings de `act()` en los tests
- [ ] El tipo `IncidentState.status` incluye `'streaming'` además de `'analyzing'`
