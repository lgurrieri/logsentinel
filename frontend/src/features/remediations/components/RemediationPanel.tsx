import { useRemediation } from '../hooks/useRemediation';
import { useRemediationExecutor } from '../hooks/useRemediationExecutor';
import { CodeBlock } from './CodeBlock';
import { ConfirmExecutionModal } from './ConfirmExecutionModal';
import { RemediationOutputTerminal } from './RemediationOutputTerminal';

interface RemediationPanelProps {
  incidentId: string;
  /** `IncidentAnalysis.suggestedScript` — `null` si la IA no generó un bloque de código parseable. */
  generatedScript: string | null;
}

/**
 * Panel de Autorización y Monitor de Ejecución de Scripts (LOG-US4-FE-03).
 *
 * Orquesta la Caja de Código Estática, el CTA de ejecución, el modal de doble
 * aprobación y la Terminal de Salida. Debe montarse dentro de `RemediationProvider`
 * (ver convención de la feature — el Provider vive en la página, no en este panel).
 */
export function RemediationPanel({ incidentId, generatedScript }: RemediationPanelProps) {
  const { state, dispatch } = useRemediation();
  const { confirmExecution } = useRemediationExecutor(incidentId);

  const isBusy = state.executionStatus === 'EXECUTING' || state.executionStatus === 'CONFIRMING';
  const ctaDisabled = !generatedScript || isBusy;

  function handleRequestConfirmation() {
    dispatch({ type: 'REQUEST_CONFIRMATION' });
  }

  function handleCancel() {
    dispatch({ type: 'CANCEL_CONFIRMATION' });
  }

  async function handleConfirm() {
    await confirmExecution();
  }

  return (
    <div className="flex flex-col gap-6">
      {generatedScript ? (
        <CodeBlock code={generatedScript} />
      ) : (
        <p className="text-zinc-400" role="status">
          No hay ningún script de remediación disponible para este incidente todavía.
        </p>
      )}

      {state.executionStatus === 'EXECUTION_FAILED' && state.errorMessage && (
        <div role="alert" className="p-4 rounded-lg border border-red-500 bg-zinc-900 text-red-400">
          {state.errorMessage}
        </div>
      )}

      <button
        type="button"
        onClick={handleRequestConfirmation}
        disabled={ctaDisabled}
        className="self-start bg-red-400 text-zinc-950 font-semibold rounded-lg px-4 py-2 disabled:opacity-50"
      >
        Ejecutar Script de Remediación
      </button>

      {state.executionStatus === 'CONFIRMING' && (
        <ConfirmExecutionModal onConfirm={handleConfirm} onCancel={handleCancel} />
      )}

      <RemediationOutputTerminal
        executionStatus={state.executionStatus}
        stdoutLog={state.result?.stdoutLog ?? null}
        stderrLog={state.result?.stderrLog ?? null}
      />
    </div>
  );
}
