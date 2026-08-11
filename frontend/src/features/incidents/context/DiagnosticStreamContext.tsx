import { createContext, useReducer } from 'react';
import type { Dispatch, ReactNode } from 'react';
import type { DiagnosticStreamAction, DiagnosticStreamState } from '../types/diagnosticStream.types';

export const initialDiagnosticStreamState: DiagnosticStreamState = {
  status: 'IDLE',
  diagnosticBuffer: '',
  errorMessage: null,
};

export function diagnosticStreamReducer(
  state: DiagnosticStreamState = initialDiagnosticStreamState,
  action: DiagnosticStreamAction,
): DiagnosticStreamState {
  switch (action.type) {
    case 'START_STREAM':
      return { ...initialDiagnosticStreamState, status: 'CONNECTING' };
    case 'RECEIVE_CHUNK':
      return {
        ...state,
        status: 'STREAMING',
        diagnosticBuffer: state.diagnosticBuffer + action.payload,
      };
    case 'CONNECTION_LOST':
      return { ...state, status: 'RECONNECTING' };
    case 'STREAM_COMPLETED':
      return { ...state, status: 'COMPLETED', errorMessage: null };
    case 'STREAM_FAILED':
      return { ...state, status: 'STREAM_FAILED', errorMessage: action.payload };
    case 'RESET':
      return initialDiagnosticStreamState;
    default:
      return state;
  }
}

interface DiagnosticStreamContextValue {
  state: DiagnosticStreamState;
  dispatch: Dispatch<DiagnosticStreamAction>;
}

export const DiagnosticStreamContext = createContext<DiagnosticStreamContextValue | null>(null);

export function DiagnosticStreamProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(diagnosticStreamReducer, initialDiagnosticStreamState);
  return (
    <DiagnosticStreamContext.Provider value={{ state, dispatch }}>
      {children}
    </DiagnosticStreamContext.Provider>
  );
}
