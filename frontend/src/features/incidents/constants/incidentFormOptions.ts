import type { Urgency } from '../types/incident.types';

/** Sistemas conocidos disponibles en el selector `systemName` (no input libre). */
export const KNOWN_SYSTEMS = ['payment-gateway', 'auth-service', 'inventory-api'] as const;

export interface UrgencyOption {
  value: Urgency;
  label: string;
  colorClass: string;
}

/** Codificación por color de cada nivel de urgencia, de menor a mayor severidad. */
export const URGENCY_OPTIONS: UrgencyOption[] = [
  { value: 'LOW', label: 'Baja', colorClass: 'text-zinc-400 border-zinc-500' },
  { value: 'MEDIUM', label: 'Media', colorClass: 'text-amber-400 border-amber-500' },
  { value: 'HIGH', label: 'Alta', colorClass: 'text-orange-400 border-orange-500' },
  { value: 'CRITICAL', label: 'Crítica', colorClass: 'text-red-400 border-red-500' },
];

/** Mínimo de caracteres exigido — igual al @Size(min=10) de CreateIncidentRequest.java. */
export const RAW_LOG_MIN_LENGTH = 10;

/** Truncamiento duro en el cliente para evitar payloads inmanejables. */
export const RAW_LOG_MAX_LENGTH = 100_000;
