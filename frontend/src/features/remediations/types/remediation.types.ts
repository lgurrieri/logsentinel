// Tipos de dominio de la feature remediations (LOG-US4-FE-03).
//
// Consumen POST /api/v1/incidents/{id}/remediations (ver `docs/openapi: 3.0.yml`),
// que es **síncrono**: la respuesta HTTP solo se resuelve una vez que el backend ya
// ejecutó el script en el sandbox y persistió el resultado final. No existe todavía un
// endpoint de consulta/streaming de progreso en ejecución (KNOWN ISSUE documentado en
// el contrato y en `LOG-US4-BE-02`) — por eso `RemediationExecutionStatus.EXECUTING` es
// un estado puramente cliente (simula la espera de la promesa in-flight), no un valor
// que el backend emita de forma incremental.

/** Estado del backend para el registro de auditoría persistido (`RemediationAction.executionStatus`). */
export type RemediationBackendStatus = 'SUCCESS' | 'FAILED' | 'DRY_RUN' | 'EXECUTING';

/**
 * Shape de `RemediationAction` (ver `docs/openapi: 3.0.yml`).
 *
 * `stdoutLog`/`stderrLog` se exponen como buffers independientes desde
 * `LOG-US4-BE-02B` — el frontend nunca debe recurrir a heurísticas de parseo de texto
 * sobre un `executionLog` combinado (ese campo ya no existe en el contrato).
 */
export interface RemediationAction {
  id: string;
  generatedScript: string;
  executionStatus: RemediationBackendStatus;
  executedAt: string;
  stdoutLog: string | null;
  stderrLog: string | null;
}

/** Máquina de estados explícita del ciclo de vida de la ejecución en el cliente (LOG-US4-FE-03). */
export type RemediationExecutionStatus =
  | 'READY'
  | 'CONFIRMING'
  | 'EXECUTING'
  | 'EXECUTION_SUCCESS'
  | 'EXECUTION_FAILED';

export interface RemediationState {
  executionStatus: RemediationExecutionStatus;
  result: RemediationAction | null;
  errorMessage: string | null;
}

export type RemediationStateAction =
  | { type: 'REQUEST_CONFIRMATION' }
  | { type: 'CANCEL_CONFIRMATION' }
  | { type: 'START_EXECUTION' }
  | { type: 'EXECUTION_COMPLETED'; payload: RemediationAction }
  | { type: 'EXECUTION_REQUEST_FAILED'; payload: string }
  | { type: 'RESET' };
