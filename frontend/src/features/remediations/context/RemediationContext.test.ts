import { describe, it, expect } from 'vitest';
import { remediationReducer, initialRemediationState } from './RemediationContext';
import type { RemediationAction } from '../types/remediation.types';

const successResult: RemediationAction = {
  id: 'rem-1',
  generatedScript: "#!/bin/bash\necho 'ok'",
  executionStatus: 'SUCCESS',
  executedAt: '2026-08-11T20:00:00Z',
  stdoutLog: 'ok',
  stderrLog: '',
};

const failedResult: RemediationAction = {
  ...successResult,
  id: 'rem-2',
  executionStatus: 'FAILED',
  stdoutLog: '',
  stderrLog: 'boom',
};

describe('remediationReducer', () => {
  it('retorna el estado inicial por defecto ante una acción desconocida', () => {
    // @ts-expect-error — acción inválida para verificar el default
    const state = remediationReducer(undefined, { type: '__UNKNOWN__' });
    expect(state).toEqual(initialRemediationState);
  });

  it('el estado inicial es READY sin resultado ni error', () => {
    expect(initialRemediationState).toEqual({
      executionStatus: 'READY',
      result: null,
      errorMessage: null,
    });
  });

  it('REQUEST_CONFIRMATION: pasa de READY a CONFIRMING', () => {
    const next = remediationReducer(initialRemediationState, { type: 'REQUEST_CONFIRMATION' });
    expect(next.executionStatus).toBe('CONFIRMING');
  });

  it('CANCEL_CONFIRMATION: vuelve de CONFIRMING a READY', () => {
    const state = { ...initialRemediationState, executionStatus: 'CONFIRMING' as const };
    const next = remediationReducer(state, { type: 'CANCEL_CONFIRMATION' });
    expect(next.executionStatus).toBe('READY');
  });

  it('START_EXECUTION: pasa a EXECUTING y limpia resultado/error previos', () => {
    const state = {
      executionStatus: 'CONFIRMING' as const,
      result: failedResult,
      errorMessage: 'error previo',
    };
    const next = remediationReducer(state, { type: 'START_EXECUTION' });

    expect(next.executionStatus).toBe('EXECUTING');
    expect(next.result).toBeNull();
    expect(next.errorMessage).toBeNull();
  });

  it('EXECUTION_COMPLETED con executionStatus SUCCESS: pasa a EXECUTION_SUCCESS y guarda el resultado', () => {
    const state = { ...initialRemediationState, executionStatus: 'EXECUTING' as const };
    const next = remediationReducer(state, { type: 'EXECUTION_COMPLETED', payload: successResult });

    expect(next.executionStatus).toBe('EXECUTION_SUCCESS');
    expect(next.result).toEqual(successResult);
    expect(next.errorMessage).toBeNull();
  });

  it('EXECUTION_COMPLETED con executionStatus FAILED: pasa a EXECUTION_FAILED y guarda el resultado', () => {
    const state = { ...initialRemediationState, executionStatus: 'EXECUTING' as const };
    const next = remediationReducer(state, { type: 'EXECUTION_COMPLETED', payload: failedResult });

    expect(next.executionStatus).toBe('EXECUTION_FAILED');
    expect(next.result).toEqual(failedResult);
  });

  it('EXECUTION_REQUEST_FAILED: pasa a EXECUTION_FAILED con mensaje genérico, sin resultado', () => {
    const state = { ...initialRemediationState, executionStatus: 'EXECUTING' as const };
    const next = remediationReducer(state, {
      type: 'EXECUTION_REQUEST_FAILED',
      payload: 'No se pudo ejecutar el script de remediación.',
    });

    expect(next.executionStatus).toBe('EXECUTION_FAILED');
    expect(next.errorMessage).toBe('No se pudo ejecutar el script de remediación.');
    expect(next.result).toBeNull();
  });

  it('RESET: restaura el estado inicial exactamente', () => {
    const state = { executionStatus: 'EXECUTION_SUCCESS' as const, result: successResult, errorMessage: null };
    const next = remediationReducer(state, { type: 'RESET' });

    expect(next).toEqual(initialRemediationState);
  });
});
