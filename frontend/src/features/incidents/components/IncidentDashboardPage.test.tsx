import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { describe, it, expect, vi, beforeAll, afterAll, afterEach } from 'vitest';
import { IncidentDashboardPage } from './IncidentDashboardPage';
import { installMockEventSource, currentSource } from '../testUtils/mockEventSource';

const API_BASE = 'http://localhost:8080';
const incidentId = 'inc-1';
const SCRIPT = '#!/bin/bash\nsystemctl restart payment-gw';

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
            rawLogSnapshot: 'ERROR: pool exhausted',
            diagnosticOutput: 'El pool de conexiones se agotó.',
            suggestedScript: SCRIPT,
            tokensUsed: 0,
            createdAt: '2026-08-11T10:05:00Z',
          },
        ],
      },
      { status: 200 },
    ),
  ),
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  server.resetHandlers();
  vi.unstubAllGlobals();
});
afterAll(() => server.close());

function renderPage(id: string = incidentId) {
  return render(
    <MemoryRouter initialEntries={[`/incidents/${id}/dashboard`]}>
      <Routes>
        <Route path="/incidents/:id/dashboard" element={<IncidentDashboardPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('IncidentDashboardPage', () => {
  it('monta la Terminal de Diagnóstico de forma independiente del fetch de detalle', () => {
    renderPage();
    expect(screen.getByRole('region', { name: /terminal de diagnóstico/i })).toBeInTheDocument();
  });

  it('monta RemediationPanel con el suggestedScript obtenido y habilita el CTA de ejecución', async () => {
    const user = userEvent.setup();
    renderPage();

    const cta = await screen.findByRole('button', { name: /ejecutar script de remediación/i });
    await waitFor(() => expect(cta).toBeEnabled());

    await user.click(cta);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('no rompe la página si el fetch de detalle falla (404) — la terminal sigue funcionando', async () => {
    server.use(
      http.get(`${API_BASE}/api/v1/incidents/${incidentId}`, () =>
        HttpResponse.json({ error: 'Not Found' }, { status: 404 }),
      ),
    );

    renderPage();

    expect(screen.getByRole('region', { name: /terminal de diagnóstico/i })).toBeInTheDocument();
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /ejecutar script de remediación/i })).toBeDisabled();
  });

  it('vuelve a habilitar el CTA de remediación sin recargar la página, una vez que el diagnóstico termina de generarse (DEBT-011/LOG-US3-FE-06)', async () => {
    let callCount = 0;
    server.use(
      http.get(`${API_BASE}/api/v1/incidents/${incidentId}`, () => {
        callCount += 1;
        return HttpResponse.json(
          {
            id: incidentId,
            systemName: 'payment-gateway',
            urgency: 'CRITICAL',
            status: 'OPEN',
            createdAt: '2026-08-11T10:00:00Z',
            analyses:
              callCount === 1
                ? []
                : [
                    {
                      id: 'analysis-1',
                      rawLogSnapshot: 'ERROR: pool exhausted',
                      diagnosticOutput: 'El pool de conexiones se agotó.',
                      suggestedScript: SCRIPT,
                      tokensUsed: 0,
                      createdAt: '2026-08-11T10:05:00Z',
                    },
                  ],
          },
          { status: 200 },
        );
      }),
    );
    installMockEventSource();

    renderPage();

    const cta = await screen.findByRole('button', { name: /ejecutar script de remediación/i });
    expect(cta).toBeDisabled();

    act(() => {
      currentSource().onmessage?.({ data: JSON.stringify({ chunk: 'Root cause found.' }) } as MessageEvent);
    });
    act(() => {
      currentSource().onerror?.(new Event('error'));
    });

    await waitFor(() => expect(cta).toBeEnabled());
    expect(callCount).toBe(2);
  });
});
