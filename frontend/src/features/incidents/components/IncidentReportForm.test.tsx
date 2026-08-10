import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { IncidentReportForm } from './IncidentReportForm';
import { createIncident, IncidentApiError } from '../api/incidentsApi';
import { RAW_LOG_MAX_LENGTH } from '../constants/incidentFormOptions';

vi.mock('../api/incidentsApi', async () => {
  const actual = await vi.importActual<typeof import('../api/incidentsApi')>('../api/incidentsApi');
  return {
    ...actual,
    createIncident: vi.fn(),
  };
});

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const mockedCreateIncident = vi.mocked(createIncident);

function renderForm() {
  return render(
    <MemoryRouter>
      <IncidentReportForm />
    </MemoryRouter>,
  );
}

const VALID_LOG = 'ERROR: connection pool exhausted after 30000ms';

async function fillValidForm() {
  await userEvent.selectOptions(screen.getByLabelText(/sistema/i), 'payment-gateway');
  await userEvent.click(screen.getByLabelText(/crítica/i));
  await userEvent.type(screen.getByLabelText(/logs/i), VALID_LOG);
}

beforeEach(() => {
  mockedCreateIncident.mockReset();
  mockNavigate.mockReset();
});

describe('IncidentReportForm', () => {
  it('renderiza el selector de sistemas conocidos, el radio group de urgencia y el textarea de logs', () => {
    renderForm();
    expect(screen.getByLabelText(/sistema/i)).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /baja/i })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /media/i })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /alta/i })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /crítica/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/logs/i)).toBeInTheDocument();
  });

  it('muestra el contador de caracteres del textarea de logs', async () => {
    renderForm();
    const textarea = screen.getByLabelText(/logs/i);
    expect(screen.getByText('0 / 100000')).toBeInTheDocument();
    await userEvent.type(textarea, 'hola');
    expect(screen.getByText('4 / 100000')).toBeInTheDocument();
  });

  it('trunca duro el textarea a 100.000 caracteres y muestra advertencia', async () => {
    renderForm();
    const textarea = screen.getByLabelText(/logs/i);
    const overLimit = 'a'.repeat(RAW_LOG_MAX_LENGTH + 50);

    await userEvent.click(textarea);
    await userEvent.paste(overLimit);

    expect((textarea as HTMLTextAreaElement).value.length).toBe(RAW_LOG_MAX_LENGTH);
    expect(screen.getByText(/límite de 100.000 caracteres/i)).toBeInTheDocument();
  });

  it('muestra errores de validación por campo y no llama a la API si el formulario está incompleto', async () => {
    renderForm();
    await userEvent.click(screen.getByRole('button', { name: /reportar incidente/i }));

    expect(await screen.findByText(/debes seleccionar un sistema/i)).toBeInTheDocument();
    expect(screen.getByText(/selecciona un nivel de urgencia/i)).toBeInTheDocument();
    expect(screen.getByText(/el volcado de logs es obligatorio/i)).toBeInTheDocument();
    expect(mockedCreateIncident).not.toHaveBeenCalled();
  });

  it('deshabilita todos los campos y el botón de envío mientras uiState es SUBMITTING', async () => {
    let resolveCreate: (value: Awaited<ReturnType<typeof createIncident>>) => void = () => {};
    mockedCreateIncident.mockReturnValue(
      new Promise((resolve) => {
        resolveCreate = resolve;
      }),
    );

    renderForm();
    await fillValidForm();
    const submitButton = screen.getByRole('button', { name: /reportar incidente/i });
    await userEvent.click(submitButton);

    expect(submitButton).toBeDisabled();
    expect(screen.getByLabelText(/sistema/i)).toBeDisabled();
    expect(screen.getByLabelText(/logs/i)).toBeDisabled();
    expect(screen.getByRole('radio', { name: /crítica/i })).toBeDisabled();

    resolveCreate({
      id: 'inc-1',
      systemName: 'payment-gateway',
      urgency: 'CRITICAL',
      status: 'OPEN',
      createdAt: '2024-01-15T10:30:00Z',
    });

    await waitFor(() => expect(mockNavigate).toHaveBeenCalled());
  });

  it('redirige a /incidents/{id}/dashboard cuando el backend responde 201', async () => {
    mockedCreateIncident.mockResolvedValue({
      id: 'inc-42',
      systemName: 'payment-gateway',
      urgency: 'CRITICAL',
      status: 'OPEN',
      createdAt: '2024-01-15T10:30:00Z',
    });

    renderForm();
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /reportar incidente/i }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/incidents/inc-42/dashboard'));
  });

  it('muestra un banner con role="alert" y vuelve a habilitar el formulario cuando el backend responde error', async () => {
    mockedCreateIncident.mockRejectedValue(new IncidentApiError(500));

    renderForm();
    await fillValidForm();
    await userEvent.click(screen.getByRole('button', { name: /reportar incidente/i }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reportar incidente/i })).not.toBeDisabled();
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
