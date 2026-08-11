import { renderHook, act } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { DiagnosticStreamProvider } from '../context/DiagnosticStreamContext';
import { useDiagnosticStream } from './useDiagnosticStream';

describe('useDiagnosticStream', () => {
  it('lanza error si se usa fuera de DiagnosticStreamProvider', () => {
    expect(() => renderHook(() => useDiagnosticStream())).toThrow(
      /debe usarse dentro de DiagnosticStreamProvider/i,
    );
  });

  it('estado inicial es IDLE con buffer vacío', () => {
    const { result } = renderHook(() => useDiagnosticStream(), {
      wrapper: DiagnosticStreamProvider,
    });

    expect(result.current.state.status).toBe('IDLE');
    expect(result.current.state.diagnosticBuffer).toBe('');
    expect(result.current.state.errorMessage).toBeNull();
  });

  it('dispatch RECEIVE_CHUNK actualiza el buffer expuesto por el hook', () => {
    const { result } = renderHook(() => useDiagnosticStream(), {
      wrapper: DiagnosticStreamProvider,
    });

    act(() => result.current.dispatch({ type: 'RECEIVE_CHUNK', payload: 'hola' }));

    expect(result.current.state.diagnosticBuffer).toBe('hola');
  });
});
