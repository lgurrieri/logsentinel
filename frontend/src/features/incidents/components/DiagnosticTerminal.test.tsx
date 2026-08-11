import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { DiagnosticStreamProvider } from '../context/DiagnosticStreamContext';
import { DiagnosticTerminal } from './DiagnosticTerminal';

type MockEventSource = {
  url: string;
  onmessage: ((e: MessageEvent) => void) | null;
  onerror: ((e: Event) => void) | null;
  close: ReturnType<typeof vi.fn>;
};

let instances: MockEventSource[] = [];

function installMockEventSource() {
  instances = [];
  vi.stubGlobal(
    'EventSource',
    vi.fn().mockImplementation(function (this: MockEventSource, url: string) {
      this.url = url;
      this.onmessage = null;
      this.onerror = null;
      this.close = vi.fn();
      instances.push(this);
    }),
  );
}

function currentSource(): MockEventSource {
  return instances[instances.length - 1];
}

function emitChunk(chunk: string) {
  act(() => {
    currentSource().onmessage?.({ data: JSON.stringify({ chunk }) } as MessageEvent);
  });
}

function emitError() {
  act(() => {
    currentSource().onerror?.(new Event('error'));
  });
}

function renderTerminal(incidentId: string | null) {
  return render(
    <DiagnosticStreamProvider>
      <DiagnosticTerminal incidentId={incidentId} />
    </DiagnosticStreamProvider>,
  );
}

/** jsdom no calcula layout real: se simulan scrollHeight/clientHeight/scrollTop. */
function mockScrollGeometry(el: HTMLElement, { scrollHeight, clientHeight, scrollTop }: {
  scrollHeight: number;
  clientHeight: number;
  scrollTop: number;
}) {
  Object.defineProperty(el, 'scrollHeight', { value: scrollHeight, configurable: true });
  Object.defineProperty(el, 'clientHeight', { value: clientHeight, configurable: true });
  Object.defineProperty(el, 'scrollTop', { value: scrollTop, writable: true, configurable: true });
}

describe('DiagnosticTerminal', () => {
  beforeEach(() => {
    installMockEventSource();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('muestra el placeholder cuando no hay diagnóstico', () => {
    renderTerminal(null);
    expect(screen.getByText(/el diagnóstico aparecerá aquí/i)).toBeInTheDocument();
  });

  it('no abre EventSource si incidentId es null', () => {
    renderTerminal(null);
    expect(EventSource).not.toHaveBeenCalled();
  });

  it('renderiza como región accesible identificable para lectores de pantalla', () => {
    renderTerminal('incident-123');
    expect(screen.getByRole('region', { name: /terminal de diagnóstico/i })).toBeInTheDocument();
  });

  it('renderiza el Markdown recibido como HTML saneado', () => {
    renderTerminal('incident-123');
    emitChunk('**RootCause:** timeout en conexión DB');

    const strong = screen.getByText('RootCause:');
    expect(strong.tagName).toBe('STRONG');
  });

  it('neutraliza scripts embebidos en el contenido del stream (XSS)', () => {
    const { container } = renderTerminal('incident-123');
    emitChunk('resultado<script>window.__pwned = true</script>');

    expect(container.querySelector('script')).toBeNull();
  });

  it('muestra el cursor parpadeante mientras el estado es STREAMING', () => {
    renderTerminal('incident-123');
    emitChunk('analizando...');
    expect(screen.getByTestId('terminal-cursor')).toBeInTheDocument();
  });

  it('muestra un mensaje de "Reconectando..." cuando la conexión se pierde sin haber recibido datos', () => {
    renderTerminal('incident-123');
    emitError();

    expect(screen.getByText(/reconectando/i)).toBeInTheDocument();
  });

  it('muestra una alerta de fallo tras agotar los reintentos', () => {
    renderTerminal('incident-123');

    for (let i = 0; i < 4; i += 1) {
      emitError();
    }

    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('auto-scrollea al fondo cuando llega un nuevo chunk y el usuario está anclado abajo', () => {
    renderTerminal('incident-123');
    const scrollEl = screen.getByTestId('diagnostic-terminal-scroll');
    mockScrollGeometry(scrollEl, { scrollHeight: 500, clientHeight: 200, scrollTop: 0 });

    emitChunk('línea nueva');

    expect(scrollEl.scrollTop).toBe(500);
  });

  it('desactiva el auto-scroll y muestra el botón flotante si el usuario sube manualmente la barra', async () => {
    const user = userEvent.setup();
    renderTerminal('incident-123');
    const scrollEl = screen.getByTestId('diagnostic-terminal-scroll');

    mockScrollGeometry(scrollEl, { scrollHeight: 500, clientHeight: 200, scrollTop: 0 });
    emitChunk('primera línea');

    // El SRE sube manualmente el scroll, alejándose del fondo
    mockScrollGeometry(scrollEl, { scrollHeight: 500, clientHeight: 200, scrollTop: 0 });
    act(() => {
      scrollEl.dispatchEvent(new Event('scroll'));
    });

    emitChunk('segunda línea mientras el usuario lee arriba');

    const newLinesButton = await screen.findByRole('button', { name: /nuevas líneas disponibles abajo/i });
    expect(newLinesButton).toBeInTheDocument();
    expect(scrollEl.scrollTop).toBe(0);

    mockScrollGeometry(scrollEl, { scrollHeight: 700, clientHeight: 200, scrollTop: 0 });
    await user.click(newLinesButton);

    expect(scrollEl.scrollTop).toBe(700);
  });
});
