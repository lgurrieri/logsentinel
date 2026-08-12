import { render, screen, fireEvent, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { CodeBlock } from './CodeBlock';

const SCRIPT = "#!/bin/bash\nsystemctl restart payment-gw";

describe('CodeBlock', () => {
  it('renderiza el script en un contenedor de solo lectura con formato monoespaciado', () => {
    render(<CodeBlock code={SCRIPT} />);
    const codeBlock = screen.getByTestId('remediation-code-block');

    expect(codeBlock).toHaveTextContent('systemctl restart payment-gw');
    expect(codeBlock.className).toMatch(/font-mono/);
  });

  it('no expone ningún control editable sobre el script', () => {
    render(<CodeBlock code={SCRIPT} />);
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('resalta la primera línea (shebang/comentario) distinta del resto del script', () => {
    render(<CodeBlock code={SCRIPT} />);
    const commentLine = screen.getByText('#!/bin/bash');

    expect(commentLine.className).toMatch(/text-zinc-500/);
  });

  it('copia el script al portapapeles y cambia temporalmente el botón a "✓ Copiado"', async () => {
    const user = userEvent.setup();
    render(<CodeBlock code={SCRIPT} />);

    // `userEvent.setup()` instala su propio stub de `navigator.clipboard` (jsdom no lo
    // implementa nativamente) — se espía en vez de reemplazarlo, para no pisar el
    // comportamiento que `user-event` ya provee en el entorno de test.
    const writeTextSpy = vi.spyOn(navigator.clipboard, 'writeText');
    const copyButton = screen.getByRole('button', { name: /copiar al portapapeles/i });
    await user.click(copyButton);

    expect(writeTextSpy).toHaveBeenCalledWith(SCRIPT);
    expect(await screen.findByRole('button', { name: /✓ copiado/i })).toBeInTheDocument();
  });

  it('vuelve a mostrar "Copiar al portapapeles" pasado el tiempo de confirmación', async () => {
    // Se usa `fireEvent` (no `userEvent`) para este caso puntual: `userEvent` gestiona
    // sus propios temporizadores internos para simular interacción real de puntero, lo
    // que es incompatible con fake timers salvo configuración adicional; `fireEvent`
    // dispara el evento DOM de forma síncrona y no interfiere con `vi.useFakeTimers()`.
    vi.useFakeTimers();
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      writable: true,
      configurable: true,
    });
    render(<CodeBlock code={SCRIPT} />);

    const copyButton = screen.getByRole('button', { name: /copiar al portapapeles/i });
    await act(async () => {
      fireEvent.click(copyButton);
      await Promise.resolve();
    });

    expect(screen.getByRole('button', { name: /✓ copiado/i })).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2100);
    });

    expect(screen.getByRole('button', { name: /^copiar al portapapeles$/i })).toBeInTheDocument();
    vi.useRealTimers();
  });
});
