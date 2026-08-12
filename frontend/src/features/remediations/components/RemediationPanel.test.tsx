import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, delay } from 'msw';
import { setupServer } from 'msw/node';
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { RemediationProvider } from '../context/RemediationContext';
import { RemediationPanel } from './RemediationPanel';

const API_BASE = 'http://localhost:8080';
const incidentId = 'inc-1';
const SCRIPT = "#!/bin/bash\nsystemctl restart payment-gw";

const server = setupServer(
  http.post(`${API_BASE}/api/v1/incidents/${incidentId}/remediations`, () =>
    HttpResponse.json(
      {
        id: 'rem-1',
        generatedScript: SCRIPT,
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

function renderPanel(generatedScript: string | null = SCRIPT) {
  return render(
    <RemediationProvider>
      <RemediationPanel incidentId={incidentId} generatedScript={generatedScript} />
    </RemediationProvider>,
  );
}

describe('RemediationPanel', () => {
  it('muestra el script sugerido en la Caja de Código Estática', () => {
    renderPanel();
    expect(screen.getByTestId('remediation-code-block')).toHaveTextContent('systemctl restart payment-gw');
  });

  it('deshabilita el CTA y muestra un mensaje cuando no hay script disponible', () => {
    renderPanel(null);

    expect(screen.getByRole('button', { name: /ejecutar script de remediación/i })).toBeDisabled();
    expect(screen.getByText(/no hay ningún script de remediación disponible/i)).toBeInTheDocument();
  });

  it('no muestra el monitor de salida antes de iniciar el proceso', () => {
    renderPanel();
    expect(screen.queryByRole('region', { name: /monitor de salida/i })).not.toBeInTheDocument();
  });

  it('abre el modal de doble aprobación al presionar el CTA, sin ejecutar de inmediato', async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(screen.getByRole('button', { name: /ejecutar script de remediación/i }));

    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('cierra el modal sin ejecutar si el operador cancela', async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(screen.getByRole('button', { name: /ejecutar script de remediación/i }));
    await user.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /ejecutar script de remediación/i })).toBeEnabled();
  });

  it('al confirmar dentro del modal: bloquea el CTA de inmediato y dispara la petición', async () => {
    // Se agrega una demora controlada a la respuesta mockeada para poder observar de
    // forma determinística el estado transitorio EXECUTING antes de que la promesa
    // (síncrona en el backend real) se resuelva.
    server.use(
      http.post(`${API_BASE}/api/v1/incidents/${incidentId}/remediations`, async () => {
        await delay(50);
        return HttpResponse.json(
          {
            id: 'rem-1',
            generatedScript: SCRIPT,
            executionStatus: 'SUCCESS',
            executedAt: '2026-08-11T20:00:00Z',
            stdoutLog: 'restarted ok',
            stderrLog: '',
          },
          { status: 200 },
        );
      }),
    );

    const user = userEvent.setup();
    renderPanel();

    await user.click(screen.getByRole('button', { name: /ejecutar script de remediación/i }));
    await user.click(screen.getByRole('button', { name: /confirmar ejecución/i }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /ejecutar script de remediación/i })).toBeDisabled();
    expect(screen.getByRole('region', { name: /monitor de salida/i })).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByRole('button', { name: /ejecutar script de remediación/i })).toBeEnabled(),
    );
    expect(screen.getByText('restarted ok')).toBeInTheDocument();
    expect(screen.getByText(/ejecución exitosa/i)).toBeInTheDocument();
  });

  it('muestra un mensaje genérico de error si la petición de ejecución falla (409/500)', async () => {
    server.use(
      http.post(`${API_BASE}/api/v1/incidents/${incidentId}/remediations`, () =>
        HttpResponse.json({ error: 'Conflict' }, { status: 409 }),
      ),
    );

    const user = userEvent.setup();
    renderPanel();

    await user.click(screen.getByRole('button', { name: /ejecutar script de remediación/i }));
    await user.click(screen.getByRole('button', { name: /confirmar ejecución/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/no se pudo ejecutar el script/i);
    expect(screen.getByRole('button', { name: /ejecutar script de remediación/i })).toBeEnabled();
  });
});
