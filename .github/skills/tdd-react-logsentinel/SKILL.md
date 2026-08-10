---
name: tdd-react-logsentinel
description: >
  Ciclo RED-GREEN-REFACTOR para el frontend de LogSentinel.
  Adaptado a Vitest + React Testing Library + Playwright.
  Usar cuando: "test del reducer", "test del hook useIncident",
  "test de componente", "test E2E", "probar SSE", "TDD frontend".
  Usar ANTES de implementar cualquier componente o hook.
---

# Test-Driven Development — Frontend LogSentinel

## Overview

Escribe el test fallido ANTES del código. Para bugs, reproduce con test primero.
Un test que pasa sin implementación no prueba nada. "Se ve bien" no es done.

## Cuándo usar

- Implementando cualquier reducer, hook, componente o API function
- Corrigiendo cualquier bug en el frontend (Prove-It Pattern)
- Antes de implementar cualquier feature en `src/features/`
- Antes de invocar `scaffold-react-feature` — el test define el contrato

**Cuándo NO usar:** Cambios de configuración pura (`vite.config.ts`, `tailwind.config.ts`), archivos de tipos sin lógica.

## Descubrir el stack primero

```bash
# Tests unitarios (Vitest)
cd frontend && npm test -- --run

# Test específico por nombre de archivo
cd frontend && npm test -- LogTerminal --run

# Tests en modo watch
cd frontend && npm test

# E2E (Playwright)
cd frontend && npx playwright test

# E2E específico
cd frontend && npx playwright test e2e/sre-flow.spec.ts
```

## El Ciclo TDD

```
    RED                  GREEN                REFACTOR
 Escribir test       Código mínimo        Limpiar sin
 que falla   ──→    para que pase  ──→  romper el test  ──→ (repetir)
     │                   │                    │
     ▼                   ▼                    ▼
 Test FALLA         Test PASA           Tests PASAN
```

### Paso 1 — RED: escribir el test que falla

El test DEBE fallar. Si pasa inmediatamente sin implementación → está mal escrito.
Verificar que falla por razón correcta: `Cannot find module`, `is not a function`, error de tipo.

### Paso 2 — GREEN: implementación mínima

Escribir el código más simple que hace pasar el test. Sin optimizar, sin casos extra.

### Paso 3 — REFACTOR: limpiar

Con el test verde: mejorar nombres, extraer helpers, eliminar duplicación.
Ejecutar el test después de CADA cambio de refactor.

## Templates por capa

### Capa 1 — Reducer (Vitest puro, sin DOM)

```typescript
// src/features/incidents/context/incidentReducer.test.ts
import { describe, it, expect } from 'vitest';
import { incidentReducer, initialState } from './IncidentContext';

describe('incidentReducer', () => {
  it('START_ANALYSIS: cambia estado a analyzing y limpia el buffer', () => {
    const state = { ...initialState, diagnosticBuffer: 'texto previo' };
    const next = incidentReducer(state, { type: 'START_ANALYSIS' });
    expect(next.status).toBe('analyzing');
    expect(next.diagnosticBuffer).toBe('');
  });

  it('RECEIVE_CHUNK: concatena el chunk al buffer existente', () => {
    const state = { ...initialState, diagnosticBuffer: 'Analizando ' };
    const next = incidentReducer(state, { type: 'RECEIVE_CHUNK', payload: 'error...' });
    expect(next.diagnosticBuffer).toBe('Analizando error...');
  });

  it('SET_ERROR: cambia estado a error con mensaje', () => {
    const next = incidentReducer(initialState, { type: 'SET_ERROR', payload: 'timeout' });
    expect(next.status).toBe('error');
    expect(next.errorMessage).toBe('timeout');
  });

  it('RESET: restaura el estado inicial exactamente', () => {
    const modified = { ...initialState, status: 'resolved' as const, diagnosticBuffer: 'texto' };
    const next = incidentReducer(modified, { type: 'RESET' });
    expect(next).toEqual(initialState);
  });
});
```

### Capa 2 — Hook (Vitest + renderHook)

```typescript
// src/features/incidents/hooks/useIncident.test.ts
import { renderHook, act } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { IncidentProvider } from '../context/IncidentContext';
import { useIncident } from './useIncident';

describe('useIncident', () => {
  it('lanza error si se usa fuera del Provider', () => {
    expect(() => renderHook(() => useIncident())).toThrow(
      /debe usarse dentro de IncidentProvider/i,
    );
  });

  it('estado inicial es idle con buffer vacío', () => {
    const { result } = renderHook(() => useIncident(), {
      wrapper: IncidentProvider,
    });
    expect(result.current.state.status).toBe('idle');
    expect(result.current.state.diagnosticBuffer).toBe('');
  });

  it('dispatch START_ANALYSIS cambia estado a analyzing', () => {
    const { result } = renderHook(() => useIncident(), {
      wrapper: IncidentProvider,
    });
    act(() => result.current.dispatch({ type: 'START_ANALYSIS' }));
    expect(result.current.state.status).toBe('analyzing');
  });
});
```

### Capa 3 — Componente (Vitest + React Testing Library)

```typescript
// src/features/incidents/components/ScenarioSelector.test.tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ScenarioSelector } from './ScenarioSelector';

describe('ScenarioSelector', () => {
  it('renderiza el selector con label accesible', () => {
    render(<ScenarioSelector onSelect={vi.fn()} />);
    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });

  it('llama onSelect con el escenario elegido', async () => {
    const onSelect = vi.fn();
    render(<ScenarioSelector onSelect={onSelect} />);
    await userEvent.selectOptions(screen.getByRole('combobox'), 'db-connection-drop');
    expect(onSelect).toHaveBeenCalledWith('db-connection-drop');
  });

  it('botón analizar deshabilitado sin selección previa', () => {
    render(<ScenarioSelector onSelect={vi.fn()} />);
    expect(screen.getByRole('button', { name: /analizar/i })).toBeDisabled();
  });
});
```

### Capa 4 — API (Vitest + MSW)

```typescript
// src/features/incidents/api/incidentsApi.test.ts
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { createIncident } from './incidentsApi';

const server = setupServer(
  http.post('http://localhost:8080/api/v1/incidents', () =>
    HttpResponse.json({ id: 'abc-123', status: 'OPEN' }, { status: 201 }),
  ),
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('createIncident', () => {
  it('retorna incidente con id y status OPEN en respuesta 201', async () => {
    const result = await createIncident({
      systemName: 'payment-gateway',
      urgency: 'CRITICAL',
      rawLogSnapshot: 'Connection timeout after 30000ms',
    });
    expect(result.id).toBe('abc-123');
    expect(result.status).toBe('OPEN');
  });

  it('lanza Error con código HTTP en respuesta 4xx', async () => {
    server.use(
      http.post('http://localhost:8080/api/v1/incidents', () =>
        HttpResponse.json({}, { status: 400 }),
      ),
    );
    await expect(
      createIncident({ systemName: '', urgency: 'CRITICAL', rawLogSnapshot: '' }),
    ).rejects.toThrow('HTTP 400');
  });
});
```

### Capa 5 — E2E Playwright (flujo crítico SRE)

```typescript
// frontend/e2e/sre-flow.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Flujo SRE principal', () => {
  test('seleccionar escenario → analizar → diagnóstico aparece en terminal', async ({ page }) => {
    await page.goto('/');

    await page.selectOption('[data-testid="scenario-selector"]', 'db-connection-drop');
    await page.click('[data-testid="analyze-btn"]');

    const terminal = page.getByRole('region', { name: /terminal de diagnóstico/i });
    await expect(terminal).toBeVisible();

    // Espera generosa para SSE — el stream puede tardar varios segundos
    await expect(terminal.getByText(/.+/)).toBeVisible({ timeout: 15_000 });
  });

  test('ejecutar remediación cambia estado a RESOLVED', async ({ page }) => {
    await page.goto('/');
    await page.selectOption('[data-testid="scenario-selector"]', 'nginx-timeout');
    await page.click('[data-testid="analyze-btn"]');

    await page.getByRole('button', { name: /ejecutar remediación/i })
      .waitFor({ timeout: 30_000 });
    await page.click('[data-testid="remediate-btn"]');

    await expect(page.getByText(/solucionado/i)).toBeVisible({ timeout: 10_000 });
  });
});
```

## Anti-patrones a evitar

| Anti-patrón | Por qué falla | Alternativa |
|---|---|---|
| Escribir componente antes del test del reducer | El reducer define el contrato de estado | Tests del reducer → hook → componente |
| Test que pasa sin implementación | No prueba nada | Si pasa inmediatamente, reescribirlo |
| `vi.mock` del Context completo | Oculta bugs de integración entre hook y Context | Usar el Provider real como `wrapper` |
| `waitFor` sin `timeout` en tests SSE | Flakiness en CI | Siempre `{ timeout: N }` explícito |
| Snapshots de componentes con Tailwind | Se rompen con cualquier cambio de clase | Aserciones sobre texto y roles accesibles |
| Verificar que dispatch fue llamado X veces | Tests de implementación, no de comportamiento | Verificar el estado resultante |

## Verificación

Después de cada ciclo RED-GREEN-REFACTOR:
- [ ] El test RED falló por la razón correcta (no por error de sintaxis)
- [ ] El test GREEN pasa con implementación mínima
- [ ] `npm test -- --run` (suite completa) pasa sin regresiones
- [ ] Nombre del test describe comportamiento, no implementación interna
- [ ] No hay `.only` ni `.skip` sin limpiar
- [ ] No hay `console.log` de debug en el código de producción
