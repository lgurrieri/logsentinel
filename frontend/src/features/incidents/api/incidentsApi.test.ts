import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { createIncident, IncidentApiError } from './incidentsApi';

const API_BASE = 'http://localhost:8080';

const server = setupServer(
  http.post(`${API_BASE}/api/v1/incidents`, () =>
    HttpResponse.json(
      {
        id: 'a5e6f7d8-0000-4000-8000-000000000000',
        systemName: 'payment-gateway',
        urgency: 'CRITICAL',
        status: 'OPEN',
        createdAt: '2024-01-15T10:30:00Z',
      },
      { status: 201 },
    ),
  ),
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

const validRequest = {
  systemName: 'payment-gateway',
  urgency: 'CRITICAL' as const,
  rawLogSnapshot: 'ERROR: pool exhausted at 2024-01-15T10:30:00Z',
};

describe('createIncident', () => {
  it('retorna el incidente creado con id y status en respuesta 201', async () => {
    const result = await createIncident(validRequest);
    expect(result.id).toBe('a5e6f7d8-0000-4000-8000-000000000000');
    expect(result.status).toBe('OPEN');
  });

  it('lanza IncidentApiError con status 400 cuando el backend rechaza la validación', async () => {
    server.use(
      http.post(`${API_BASE}/api/v1/incidents`, () =>
        HttpResponse.json(
          { error: 'Validation Failed', fieldErrors: [{ field: 'systemName', message: 'must not be blank' }] },
          { status: 400 },
        ),
      ),
    );

    await expect(createIncident(validRequest)).rejects.toMatchObject(
      new IncidentApiError(400),
    );
  });

  it('lanza IncidentApiError con status 500 cuando el backend falla inesperadamente', async () => {
    server.use(
      http.post(`${API_BASE}/api/v1/incidents`, () =>
        HttpResponse.json(
          { error: 'Internal Server Error', message: 'An unexpected error occurred' },
          { status: 500 },
        ),
      ),
    );

    await expect(createIncident(validRequest)).rejects.toMatchObject(
      new IncidentApiError(500),
    );
  });
});
