import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { renderHook, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { useIncidentDetail } from './useIncidentDetail';

const API_BASE = 'http://localhost:8080';
const incidentId = 'inc-1';

const server = setupServer(
  http.get(`${API_BASE}/api/v1/incidents/${incidentId}`, () =>
    HttpResponse.json(
      {
        id: incidentId,
        systemName: 'payment-gateway',
        urgency: 'CRITICAL',
        status: 'OPEN',
        createdAt: '2026-08-11T10:00:00Z',
        analyses: [
          {
            id: 'analysis-1',
            rawLogSnapshot: 'ERROR: pool exhausted (attempt 1)',
            diagnosticOutput: 'Diagnóstico previo.',
            suggestedScript: 'echo previo',
            tokensUsed: 0,
            createdAt: '2026-08-11T10:05:00Z',
          },
          {
            id: 'analysis-2',
            rawLogSnapshot: 'ERROR: pool exhausted (attempt 2)',
            diagnosticOutput: 'El pool de conexiones se agotó.',
            suggestedScript: 'systemctl restart payment-gw',
            tokensUsed: 0,
            createdAt: '2026-08-11T10:10:00Z',
          },
        ],
      },
      { status: 200 },
    ),
  ),
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('useIncidentDetail', () => {
  it('no dispara fetch si incidentId es null', () => {
    const { result } = renderHook(() => useIncidentDetail(null));
    expect(result.current.isLoading).toBe(false);
    expect(result.current.suggestedScript).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it('expone isLoading=true mientras la petición está en curso', () => {
    const { result } = renderHook(() => useIncidentDetail(incidentId));
    expect(result.current.isLoading).toBe(true);
  });

  it('expone el suggestedScript del análisis más reciente (último del array) tras resolver', async () => {
    const { result } = renderHook(() => useIncidentDetail(incidentId));

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.suggestedScript).toBe('systemctl restart payment-gw');
    expect(result.current.incidentDetail?.analyses).toHaveLength(2);
    expect(result.current.error).toBeNull();
  });

  it('expone un mensaje de error genérico sin romper si el fetch falla (404)', async () => {
    server.use(
      http.get(`${API_BASE}/api/v1/incidents/${incidentId}`, () =>
        HttpResponse.json({ error: 'Not Found' }, { status: 404 }),
      ),
    );

    const { result } = renderHook(() => useIncidentDetail(incidentId));

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.error).toBeTruthy();
    expect(result.current.error).not.toMatch(/404/i);
    expect(result.current.suggestedScript).toBeNull();
  });
});
