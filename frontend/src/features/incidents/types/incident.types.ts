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
