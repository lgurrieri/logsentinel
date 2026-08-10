import type { IncidentFormData, IncidentFormErrors } from '../types/incident.types';
import { RAW_LOG_MIN_LENGTH } from '../constants/incidentFormOptions';

/**
 * Validación client-side pre-envío. Replica las mismas reglas mínimas que ya
 * exige el backend (@NotBlank + @Size(min=10)) para evitar un viaje de red inútil.
 */
export function validateIncidentForm(data: IncidentFormData): IncidentFormErrors {
  const errors: IncidentFormErrors = {};

  if (!data.systemName) {
    errors.systemName = 'Debes seleccionar un sistema.';
  }

  if (!data.urgency) {
    errors.urgency = 'Selecciona un nivel de urgencia.';
  }

  const trimmedLog = data.rawLogSnapshot.trim();
  if (trimmedLog.length === 0) {
    errors.rawLogSnapshot = 'El volcado de logs es obligatorio.';
  } else if (data.rawLogSnapshot.length < RAW_LOG_MIN_LENGTH) {
    errors.rawLogSnapshot = `El volcado de logs debe tener al menos ${RAW_LOG_MIN_LENGTH} caracteres.`;
  }

  return errors;
}
