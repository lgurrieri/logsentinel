// Tipos de dominio de la consola de diagnóstico en streaming (LOG-US3-FE-03).
//
// Consumen GET /api/v1/incidents/{id}/diagnostic/stream (ver `docs/openapi: 3.0.yml`),
// que emite eventos SSE con forma `data: {"chunk": "texto parcial"}` — ver
// `SseDiagnosticStreamListener.onChunk` en el backend (LOG-US3-BE-01). El contrato no
// define un evento explícito de cierre exitoso: el emitter simplemente cierra la
// conexión HTTP con `emitter.complete()` tras el último chunk (ver
// `useDiagnosticStreamConnection` para cómo se resuelve esa ambigüedad en el cliente).

/** Máquina de estados explícita del ciclo de vida de la conexión SSE del diagnóstico. */
export type DiagnosticStreamStatus =
  | 'IDLE'
  | 'CONNECTING'
  | 'STREAMING'
  | 'RECONNECTING'
  | 'COMPLETED'
  | 'STREAM_FAILED';

export interface DiagnosticStreamState {
  status: DiagnosticStreamStatus;
  /** Texto Markdown acumulado token a token desde el LLM. */
  diagnosticBuffer: string;
  errorMessage: string | null;
}

export type DiagnosticStreamAction =
  | { type: 'START_STREAM' }
  | { type: 'RECEIVE_CHUNK'; payload: string }
  | { type: 'CONNECTION_LOST' }
  | { type: 'STREAM_COMPLETED' }
  | { type: 'STREAM_FAILED'; payload: string }
  | { type: 'RESET' };

/** Shape cruda del payload SSE emitido por `SseDiagnosticStreamListener.onChunk`. */
export interface DiagnosticChunkPayload {
  chunk?: string;
}
