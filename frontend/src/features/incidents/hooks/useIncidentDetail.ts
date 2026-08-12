import { useEffect, useState } from 'react';
import { getIncidentDetail } from '../api/incidentsApi';
import type { IncidentDetail } from '../types/incident.types';

const GENERIC_FETCH_FAILED_MESSAGE = 'No se pudo cargar el detalle del incidente.';

interface UseIncidentDetailResult {
  incidentDetail: IncidentDetail | null;
  /** `suggestedScript` del análisis más reciente (último elemento de `analyses`), o `null`. */
  suggestedScript: string | null;
  isLoading: boolean;
  error: string | null;
}

/**
 * Consume `GET /api/v1/incidents/{id}` (`getIncidentDetail`, ver `docs/openapi: 3.0.yml`)
 * y expone el `suggestedScript` del diagnóstico más reciente para montar
 * `RemediationPanel` en `IncidentDashboardPage` (LOG-US4-FE-04, resolución de
 * `DEBT-003`).
 *
 * Fetch aislado del resto de la página: un error acá nunca lanza una excepción hacia el
 * árbol de React (se captura y expone como `error` genérico) — `DiagnosticTerminal`
 * (alimentado por `useDiagnosticStreamConnection`, independiente de este hook) sigue
 * funcionando sin verse afectado.
 */
export function useIncidentDetail(incidentId: string | null): UseIncidentDetailResult {
  const [incidentDetail, setIncidentDetail] = useState<IncidentDetail | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!incidentId) return;

    let cancelled = false;
    setIsLoading(true);
    setError(null);

    getIncidentDetail(incidentId)
      .then((detail) => {
        if (cancelled) return;
        setIncidentDetail(detail);
      })
      .catch(() => {
        // Nunca se expone el status/mensaje técnico del backend al operador (DevSecOps)
        // — 404, 5xx o falla de red resultan en el mismo mensaje genérico.
        if (cancelled) return;
        setError(GENERIC_FETCH_FAILED_MESSAGE);
      })
      .finally(() => {
        if (cancelled) return;
        setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [incidentId]);

  const latestAnalysis = incidentDetail?.analyses.at(-1) ?? null;

  return {
    incidentDetail,
    suggestedScript: latestAnalysis?.suggestedScript ?? null,
    isLoading,
    error,
  };
}
