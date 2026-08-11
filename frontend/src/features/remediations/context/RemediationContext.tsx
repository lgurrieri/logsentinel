import { createContext, useReducer } from 'react';
import type { Dispatch, ReactNode } from 'react';
import type { RemediationState, RemediationStateAction } from '../types/remediation.types';

export const initialRemediationState: RemediationState = {
  executionStatus: 'READY',
  result: null,
  errorMessage: null,
};

export function remediationReducer(
  state: RemediationState = initialRemediationState,
  action: RemediationStateAction,
): RemediationState {
  switch (action.type) {
    case 'REQUEST_CONFIRMATION':
      return { ...state, executionStatus: 'CONFIRMING' };
    case 'CANCEL_CONFIRMATION':
      return { ...state, executionStatus: 'READY' };
    case 'START_EXECUTION':
      return { ...state, executionStatus: 'EXECUTING', result: null, errorMessage: null };
    case 'EXECUTION_COMPLETED':
      return {
        ...state,
        executionStatus: action.payload.executionStatus === 'SUCCESS' ? 'EXECUTION_SUCCESS' : 'EXECUTION_FAILED',
        result: action.payload,
        errorMessage: null,
      };
    case 'EXECUTION_REQUEST_FAILED':
      return { ...state, executionStatus: 'EXECUTION_FAILED', result: null, errorMessage: action.payload };
    case 'RESET':
      return initialRemediationState;
    default:
      return state;
  }
}

interface RemediationContextValue {
  state: RemediationState;
  dispatch: Dispatch<RemediationStateAction>;
}

export const RemediationContext = createContext<RemediationContextValue | null>(null);

export function RemediationProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(remediationReducer, initialRemediationState);
  return (
    <RemediationContext.Provider value={{ state, dispatch }}>{children}</RemediationContext.Provider>
  );
}
