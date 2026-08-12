// Tipos de dominio de la feature incidents.
// Reflejan el contrato de POST /api/v1/incidents (ver CreateIncidentRequest.java /
// IncidentResponse.java / GlobalExceptionHandler.java en el backend).

export type Urgency = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/** Máquina de estados explícita del ciclo de vida del formulario. */
export type IncidentUiState = 'IDLE' | 'SUBMITTING' | 'SUCCESS' | 'SERVER_ERROR';

/** Estado local e inmutable de los datos del formulario. */
export interface IncidentFormData {
  systemName: string;
  urgency: Urgency | '';
  rawLogSnapshot: string;
}

/** Errores de validación por campo, mostrados antes de intentar el envío. */
export interface IncidentFormErrors {
  systemName?: string;
  urgency?: string;
  rawLogSnapshot?: string;
}

export interface CreateIncidentRequest {
  systemName: string;
  urgency: Urgency;
  rawLogSnapshot: string;
}

export interface IncidentResponse {
  id: string;
  systemName: string;
  urgency: Urgency;
  status: string;
  createdAt: string;
}

export interface IncidentFieldError {
  field: string;
  message: string;
}

// Tipos de GET /api/v1/incidents/{id} (LOG-US4-BE-03 / LOG-US4-FE-04, ver
// `docs/openapi: 3.0.yml` — schemas `IncidentDetail` e `IncidentAnalysis`).

export type IncidentStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';

/**
 * Diagnóstico de IA persistido para un incidente (`IncidentDiagnostic` en el backend).
 * `tokensUsed` es un placeholder `0` sin fuente de datos real todavía (`DEBT-004`,
 * no bloqueante) — el frontend no necesita tratarlo de forma especial.
 */
export interface IncidentAnalysis {
  id: string;
  rawLogSnapshot: string;
  diagnosticOutput: string;
  /** `null` si la IA no generó un bloque de código parseable. */
  suggestedScript: string | null;
  tokensUsed: number;
  createdAt: string;
}

/** `Incident` + `analyses: IncidentAnalysis[]` — ver schema `IncidentDetail` del contrato. */
export interface IncidentDetail {
  id: string;
  systemName: string;
  urgency: Urgency;
  status: IncidentStatus;
  createdAt: string;
  analyses: IncidentAnalysis[];
}
