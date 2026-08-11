import '@testing-library/jest-dom';

/**
 * jsdom no implementa `EventSource` (Server-Sent Events) de forma nativa.
 *
 * Se registra un stub inerte como valor global por defecto para que cualquier
 * componente que abra un canal SSE al montarse (p.ej. `DiagnosticTerminal`,
 * LOG-US3-FE-03) pueda renderizarse en pruebas que no necesitan controlar la
 * conexión explícitamente (p.ej. `App.test.tsx`), sin lanzar
 * `ReferenceError: EventSource is not defined`.
 *
 * Los tests que sí necesitan observar mensajes/errores del stream sustituyen
 * este stub puntualmente en su propio archivo vía
 * `vi.stubGlobal('EventSource', ...)` + `vi.unstubAllGlobals()` en `afterEach`
 * (ver `DiagnosticTerminal.test.tsx` / `useDiagnosticStreamConnection.test.tsx`).
 */
interface EventSourceEventMap {
  error: Event;
  message: MessageEvent;
  open: Event;
}

class NoopEventSource implements EventSource {
  static readonly CONNECTING = 0 as const;
  static readonly OPEN = 1 as const;
  static readonly CLOSED = 2 as const;

  readonly CONNECTING = 0 as const;
  readonly OPEN = 1 as const;
  readonly CLOSED = 2 as const;

  readonly url: string;
  readonly withCredentials = false;
  readyState: number = this.CONNECTING;
  onerror: ((this: EventSource, ev: Event) => unknown) | null = null;
  onmessage: ((this: EventSource, ev: MessageEvent) => unknown) | null = null;
  onopen: ((this: EventSource, ev: Event) => unknown) | null = null;

  constructor(url: string | URL) {
    this.url = url.toString();
  }

  close(): void {
    this.readyState = this.CLOSED;
  }

  addEventListener<K extends keyof EventSourceEventMap>(
    type: K,
    listener: (this: EventSource, ev: EventSourceEventMap[K]) => unknown,
    options?: boolean | AddEventListenerOptions,
  ): void;
  addEventListener(
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | AddEventListenerOptions,
  ): void;
  addEventListener(): void {
    // Stub inerte: este canal simulado nunca dispara eventos reales.
  }

  removeEventListener<K extends keyof EventSourceEventMap>(
    type: K,
    listener: (this: EventSource, ev: EventSourceEventMap[K]) => unknown,
    options?: boolean | EventListenerOptions,
  ): void;
  removeEventListener(
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | EventListenerOptions,
  ): void;
  removeEventListener(): void {
    // Stub inerte.
  }

  dispatchEvent(_event: Event): boolean {
    return true;
  }
}

globalThis.EventSource = NoopEventSource;
