import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import type { ReactNode } from 'react';
import { DiagnosticStreamProvider } from '../context/DiagnosticStreamContext';
import { useDiagnosticStream } from './useDiagnosticStream';
import { useDiagnosticStreamConnection } from './useDiagnosticStreamConnection';
import { installMockEventSource, currentSource, instanceCount } from '../testUtils/mockEventSource';

function wrapper({ children }: { children: ReactNode }) {
  return <DiagnosticStreamProvider>{children}</DiagnosticStreamProvider>;
}

function renderConnection(incidentId: string | null) {
  return renderHook(
    () => {
      const stream = useDiagnosticStream();
      useDiagnosticStreamConnection(incidentId);
      return stream;
    },
    { wrapper },
  );
}

describe('useDiagnosticStreamConnection', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    installMockEventSource();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('no abre EventSource si incidentId es null', () => {
    renderConnection(null);
    expect(EventSource).not.toHaveBeenCalled();
  });

  it('conecta al path /api/v1/incidents/{id}/diagnostic/stream', () => {
    renderConnection('incident-123');
    expect(currentSource().url).toBe(
      'http://localhost:8080/api/v1/incidents/incident-123/diagnostic/stream',
    );
  });

  it('parsea el payload {"chunk": "..."} y concatena el fragmento al buffer', () => {
    const { result } = renderConnection('incident-123');

    act(() => {
      currentSource().onmessage?.({ data: JSON.stringify({ chunk: 'RootCause: timeout DB' }) } as MessageEvent);
    });

    expect(result.current.state.status).toBe('STREAMING');
    expect(result.current.state.diagnosticBuffer).toBe('RootCause: timeout DB');
  });

  it('cierra el EventSource al desmontar — sin leak', () => {
    const { unmount } = renderConnection('incident-123');
    const source = currentSource();
    unmount();
    expect(source.close).toHaveBeenCalledTimes(1);
  });

  it('trata un cierre sin haber recibido ningún chunk como falla real y reintenta con backoff', () => {
    const { result } = renderConnection('incident-123');

    act(() => {
      currentSource().onerror?.(new Event('error'));
    });
    expect(result.current.state.status).toBe('RECONNECTING');

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(instanceCount()).toBe(2);

    act(() => {
      currentSource().onerror?.(new Event('error'));
    });
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(instanceCount()).toBe(3);

    act(() => {
      currentSource().onerror?.(new Event('error'));
    });
    act(() => {
      vi.advanceTimersByTime(4000);
    });
    expect(instanceCount()).toBe(4);

    // Cuarto intento agotado (MAX_RETRIES = 3) → STREAM_FAILED, sin más reconexiones
    act(() => {
      currentSource().onerror?.(new Event('error'));
    });

    expect(result.current.state.status).toBe('STREAM_FAILED');
    expect(result.current.state.errorMessage).toBeTruthy();

    act(() => {
      vi.advanceTimersByTime(10_000);
    });
    expect(instanceCount()).toBe(4);
  });

  it('trata un cierre tras haber recibido al menos un chunk como fin exitoso del stream (sin reintentar)', () => {
    const { result } = renderConnection('incident-123');

    act(() => {
      currentSource().onmessage?.({ data: JSON.stringify({ chunk: 'Diagnóstico completo.' }) } as MessageEvent);
    });
    act(() => {
      currentSource().onerror?.(new Event('error'));
    });

    expect(result.current.state.status).toBe('COMPLETED');
    expect(result.current.state.diagnosticBuffer).toBe('Diagnóstico completo.');

    act(() => {
      vi.advanceTimersByTime(10_000);
    });
    expect(instanceCount()).toBe(1);
  });
});
