import { useCallback, useEffect } from 'react';
import { useRemediation } from './useRemediation';
import { executeRemediation } from '../api/remediationsApi';

const GENERIC_EXECUTION_FAILED_MESSAGE =
  'No se pudo ejecutar el script de remediación. Contactá al equipo de plataforma si el problema persiste.';

/**
 * Encapsula la petición `POST /api/v1/incidents/{id}/remediations` (síncrona — ver
 * `remediationsApi.executeRemediation`) y el bloqueo de los controles de cierre de
 * pantalla mientras `executionStatus === 'EXECUTING'` (LOG-US4-FE-03): al confirmar
 * dentro del modal, el estado pasa a `EXECUTING` de inmediato (antes de que la promesa
 * resuelva) y el hook registra un guard de `beforeunload` para impedir que el operador
 * cierre o recargue la pestaña e interrumpa el ciclo desde el cliente mientras el
 * script corre contra producción.
 */
export function useRemediationExecutor(incidentId: string) {
  const { state, dispatch } = useRemediation();

  useEffect(() => {
    if (state.executionStatus !== 'EXECUTING') return;

    function handleBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault();
      event.returnValue = '';
    }

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [state.executionStatus]);

  const confirmExecution = useCallback(async () => {
    dispatch({ type: 'START_EXECUTION' });
    try {
      const result = await executeRemediation(incidentId);
      dispatch({ type: 'EXECUTION_COMPLETED', payload: result });
    } catch {
      // Nunca se expone el status/mensaje técnico del backend al operador (DevSecOps) —
      // sea un 409 (sin diagnóstico/script sugerido), un 5xx o una falla de red, el
      // resultado visible en la UI es siempre el mismo mensaje genérico.
      dispatch({ type: 'EXECUTION_REQUEST_FAILED', payload: GENERIC_EXECUTION_FAILED_MESSAGE });
    }
  }, [incidentId, dispatch]);

  return { confirmExecution };
}
