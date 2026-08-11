import { describe, it, expect } from 'vitest';
import {
  diagnosticStreamReducer,
  initialDiagnosticStreamState,
} from './DiagnosticStreamContext';

describe('diagnosticStreamReducer', () => {
  it('retorna el estado inicial por defecto ante una acción desconocida', () => {
    // @ts-expect-error — acción inválida para verificar el default
    const state = diagnosticStreamReducer(undefined, { type: '__UNKNOWN__' });
    expect(state).toEqual(initialDiagnosticStreamState);
  });

  it('START_STREAM: limpia el buffer previo y pasa a CONNECTING', () => {
    const previous = {
      ...initialDiagnosticStreamState,
      diagnosticBuffer: 'texto de un analisis anterior',
      status: 'STREAM_FAILED' as const,
      errorMessage: 'algo fallo antes',
    };

    const next = diagnosticStreamReducer(previous, { type: 'START_STREAM' });

    expect(next.status).toBe('CONNECTING');
    expect(next.diagnosticBuffer).toBe('');
    expect(next.errorMessage).toBeNull();
  });

  it('RECEIVE_CHUNK: concatena el chunk al buffer existente y pasa a STREAMING', () => {
    const state = { ...initialDiagnosticStreamState, diagnosticBuffer: 'Root cause: ' };
    const next = diagnosticStreamReducer(state, { type: 'RECEIVE_CHUNK', payload: 'timeout en DB' });

    expect(next.status).toBe('STREAMING');
    expect(next.diagnosticBuffer).toBe('Root cause: timeout en DB');
  });

  it('CONNECTION_LOST: pasa a RECONNECTING sin perder el buffer ya recibido', () => {
    const state = { ...initialDiagnosticStreamState, status: 'STREAMING' as const, diagnosticBuffer: 'parcial...' };
    const next = diagnosticStreamReducer(state, { type: 'CONNECTION_LOST' });

    expect(next.status).toBe('RECONNECTING');
    expect(next.diagnosticBuffer).toBe('parcial...');
  });

  it('STREAM_COMPLETED: pasa a COMPLETED y limpia cualquier error previo', () => {
    const state = {
      ...initialDiagnosticStreamState,
      status: 'STREAMING' as const,
      diagnosticBuffer: 'diagnostico completo',
    };
    const next = diagnosticStreamReducer(state, { type: 'STREAM_COMPLETED' });

    expect(next.status).toBe('COMPLETED');
    expect(next.errorMessage).toBeNull();
    expect(next.diagnosticBuffer).toBe('diagnostico completo');
  });

  it('STREAM_FAILED: pasa a STREAM_FAILED con el mensaje de error provisto', () => {
    const next = diagnosticStreamReducer(initialDiagnosticStreamState, {
      type: 'STREAM_FAILED',
      payload: 'No se pudo restablecer la conexión con el agente de diagnóstico.',
    });

    expect(next.status).toBe('STREAM_FAILED');
    expect(next.errorMessage).toBe('No se pudo restablecer la conexión con el agente de diagnóstico.');
  });

  it('RESET: restaura el estado inicial exactamente', () => {
    const modified = {
      ...initialDiagnosticStreamState,
      status: 'COMPLETED' as const,
      diagnosticBuffer: 'texto',
    };
    const next = diagnosticStreamReducer(modified, { type: 'RESET' });

    expect(next).toEqual(initialDiagnosticStreamState);
  });
});
