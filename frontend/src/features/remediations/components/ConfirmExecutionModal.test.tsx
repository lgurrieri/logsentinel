import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ConfirmExecutionModal } from './ConfirmExecutionModal';

describe('ConfirmExecutionModal', () => {
  it('se renderiza como un diálogo modal accesible', () => {
    render(<ConfirmExecutionModal onConfirm={vi.fn()} onCancel={vi.fn()} />);

    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
  });

  it('muestra la advertencia explícita de doble aprobación', () => {
    render(<ConfirmExecutionModal onConfirm={vi.fn()} onCancel={vi.fn()} />);

    expect(
      screen.getByText(
        /¿confirmas la ejecución de este comando en el sistema de producción\? esta acción quedará registrada bajo tu firma de auditoría\./i,
      ),
    ).toBeInTheDocument();
  });

  it('fuerza el foco de teclado hacia dentro del modal al montarse', () => {
    render(<ConfirmExecutionModal onConfirm={vi.fn()} onCancel={vi.fn()} />);

    expect(screen.getByRole('dialog')).toContainElement(document.activeElement as HTMLElement);
  });

  it('invoca onConfirm al presionar "Confirmar Ejecución"', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<ConfirmExecutionModal onConfirm={onConfirm} onCancel={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: /confirmar ejecución/i }));

    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('invoca onCancel al presionar "Cancelar"', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    render(<ConfirmExecutionModal onConfirm={vi.fn()} onCancel={onCancel} />);

    await user.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
