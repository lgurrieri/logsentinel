import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { executeRemediation, RemediationApiError } from './remediationsApi';

const API_BASE = 'http://localhost:8080';
const incidentId = 'a5e6f7d8-0000-4000-8000-000000000000';

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

describe('executeRemediation', () => {
  it('dispara POST /api/v1/incidents/{id}/remediations sin body y retorna el RemediationAction', async () => {
    const result = await executeRemediation(incidentId);

    expect(result.id).toBe('rem-1');
    expect(result.executionStatus).toBe('SUCCESS');
    expect(result.stdoutLog).toBe('restarted ok');
    expect(result.stderrLog).toBe('');
  });

  it('lanza RemediationApiError con status 409 cuando no hay diagnóstico persistido con script sugerido', async () => {
    server.use(
      http.post(`${API_BASE}/api/v1/incidents/${incidentId}/remediations`, () =>
        HttpResponse.json({ error: 'Conflict' }, { status: 409 }),
      ),
    );

    await expect(executeRemediation(incidentId)).rejects.toMatchObject(new RemediationApiError(409));
  });

  it('lanza RemediationApiError con status 500 cuando el backend falla inesperadamente', async () => {
    server.use(
      http.post(`${API_BASE}/api/v1/incidents/${incidentId}/remediations`, () =>
        HttpResponse.json({ error: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    await expect(executeRemediation(incidentId)).rejects.toMatchObject(new RemediationApiError(500));
  });
});
