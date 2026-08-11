import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeAll, afterAll, afterEach, vi } from 'vitest';
import type { ReactNode } from 'react';
import { RemediationProvider } from '../context/RemediationContext';
import { useRemediation } from './useRemediation';
import { useRemediationExecutor } from './useRemediationExecutor';

const API_BASE = 'http://localhost:8080';
const incidentId = 'inc-1';

const server = setupServer(
  http.post(`${API_BASE}/api/v1/incidents/${incidentId}/remediations`, () =>
    HttpResponse.json(
      {
        id: 'rem-1',
        generatedScript: "#!/bin/bash\nsystemctl restart payment-gw",
        executionStatus: 'SUCCESS',
        executedAt: '2026-08-11T20:00:00Z',
        stdoutLog: 'restarted ok',
        stderrLog: '',
      },
      { status: 200 },
    ),
  ),
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function wrapper({ children }: { children: ReactNode }) {
  return <RemediationProvider>{children}</RemediationProvider>;
}

function renderExecutor() {
  return renderHook(
    () => ({
      remediation: useRemediation(),
      executor: useRemediationExecutor(incidentId),
    }),
    { wrapper },
  );
}

describe('useRemediationExecutor', () => {
  it('confirmExecution: pasa a EXECUTING de inmediato y a EXECUTION_SUCCESS al resolver', async () => {
    const { result } = renderExecutor();

    act(() => {
      void result.current.executor.confirmExecution();
    });
    expect(result.current.remediation.state.executionStatus).toBe('EXECUTING');

    await waitFor(() => expect(result.current.remediation.state.executionStatus).toBe('EXECUTION_SUCCESS'));
    expect(result.current.remediation.state.result?.stdoutLog).toBe('restarted ok');
  });

  it('confirmExecution: pasa a EXECUTION_FAILED con mensaje genérico si el backend responde 409', async () => {
    server.use(
      http.post(`${API_BASE}/api/v1/incidents/${incidentId}/remediations`, () =>
        HttpResponse.json({ error: 'Conflict' }, { status: 409 }),
      ),
    );

    const { result } = renderExecutor();

    await act(async () => {
      await result.current.executor.confirmExecution();
    });

    expect(result.current.remediation.state.executionStatus).toBe('EXECUTION_FAILED');
    expect(result.current.remediation.state.errorMessage).toMatch(/no se pudo ejecutar/i);
    expect(result.current.remediation.state.errorMessage).not.toMatch(/conflict/i);
  });

  it('bloquea el cierre/recarga de la pestaña mientras executionStatus es EXECUTING', async () => {
    const { result } = renderExecutor();

    const beforeUnloadEvent = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
    const preventDefaultSpy = vi.spyOn(beforeUnloadEvent, 'preventDefault');

    act(() => {
      void result.current.executor.confirmExecution();
    });
    expect(result.current.remediation.state.executionStatus).toBe('EXECUTING');

    act(() => {
      window.dispatchEvent(beforeUnloadEvent);
    });

    expect(preventDefaultSpy).toHaveBeenCalled();

    await waitFor(() => expect(result.current.remediation.state.executionStatus).toBe('EXECUTION_SUCCESS'));
  });

  it('no bloquea el cierre de la pestaña en estado READY', () => {
    const { result } = renderExecutor();
    expect(result.current.remediation.state.executionStatus).toBe('READY');

    const beforeUnloadEvent = new Event('beforeunload', { cancelable: true }) as BeforeUnloadEvent;
    const preventDefaultSpy = vi.spyOn(beforeUnloadEvent, 'preventDefault');

    act(() => {
      window.dispatchEvent(beforeUnloadEvent);
    });

    expect(preventDefaultSpy).not.toHaveBeenCalled();
  });
});
