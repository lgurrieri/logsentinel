import { renderHook, act } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { RemediationProvider } from '../context/RemediationContext';
import { useRemediation } from './useRemediation';

describe('useRemediation', () => {
  it('lanza error si se usa fuera de RemediationProvider', () => {
    expect(() => renderHook(() => useRemediation())).toThrow(/debe usarse dentro de RemediationProvider/i);
  });

  it('estado inicial es READY sin resultado', () => {
    const { result } = renderHook(() => useRemediation(), { wrapper: RemediationProvider });

    expect(result.current.state.executionStatus).toBe('READY');
    expect(result.current.state.result).toBeNull();
  });

  it('dispatch REQUEST_CONFIRMATION actualiza el estado expuesto por el hook', () => {
    const { result } = renderHook(() => useRemediation(), { wrapper: RemediationProvider });

    act(() => result.current.dispatch({ type: 'REQUEST_CONFIRMATION' }));

    expect(result.current.state.executionStatus).toBe('CONFIRMING');
  });
});
