import type { RemediationExecutionStatus } from '../types/remediation.types';

interface RemediationOutputTerminalProps {
  executionStatus: RemediationExecutionStatus;
  stdoutLog: string | null;
  stderrLog: string | null;
}

function splitLines(log: string | null): string[] {
  if (!log) return [];
  return log.split('\n').filter((line) => line.length > 0);
}

/**
 * Terminal Secundaria de Salida / Output Monitor (LOG-US4-FE-03).
 *
 * Panel inferior de consola inicialmente ausente: solo aparece al arrancar el proceso
 * (`executionStatus !== 'READY' | 'CONFIRMING'`). El backend `POST .../remediations`
 * es síncrono (no hay progreso incremental real que consultar — ver nota en
 * `remediation.types.ts`), así que mientras `EXECUTING` se muestra un placeholder de
 * carga; recién al resolver la petición se pintan `stdoutLog`/`stderrLog` (buffers
 * independientes, `LOG-US4-BE-02B`) con formateo defensivo: `stdout` en gris claro
 * ordinario, `stderr` antepuesto por `[ERROR]` en rojo brillante de alerta.
 */
export function RemediationOutputTerminal({
  executionStatus,
  stdoutLog,
  stderrLog,
}: RemediationOutputTerminalProps) {
  if (executionStatus === 'READY' || executionStatus === 'CONFIRMING') {
    return null;
  }

  const isExecuting = executionStatus === 'EXECUTING';
  const stdoutLines = splitLines(stdoutLog);
  const stderrLines = splitLines(stderrLog);

  return (
    <section
      aria-label="Monitor de salida de la remediación"
      aria-busy={isExecuting}
      className="flex flex-col bg-zinc-950 border border-zinc-700 rounded-lg overflow-hidden"
    >
      <div className="flex items-center gap-2 px-4 py-2 bg-zinc-900 border-b border-zinc-700">
        <span
          className={`h-2 w-2 rounded-full ${
            isExecuting
              ? 'bg-amber-400 animate-pulse'
              : executionStatus === 'EXECUTION_SUCCESS'
                ? 'bg-green-400'
                : 'bg-red-400'
          }`}
        />
        <span className="text-xs text-zinc-400 font-mono">
          {isExecuting && 'Ejecutando script en la infraestructura simulada...'}
          {executionStatus === 'EXECUTION_SUCCESS' && '✓ Ejecución exitosa'}
          {executionStatus === 'EXECUTION_FAILED' && '✗ Ejecución fallida'}
        </span>
      </div>

      <div className="p-4 font-mono text-sm whitespace-pre-wrap break-words min-h-24">
        {isExecuting && (
          <p className="text-zinc-500 animate-pulse">Esperando el resultado del script…</p>
        )}

        {!isExecuting && (
          <>
            {stdoutLines.map((line, index) => (
              <div key={`stdout-${index}`} className="text-zinc-300">
                {line}
              </div>
            ))}
            {stderrLines.map((line, index) => (
              <div key={`stderr-${index}`} className="text-red-400 font-semibold">
                {`[ERROR] ${line}`}
              </div>
            ))}
            {stdoutLines.length === 0 && stderrLines.length === 0 && (
              <p className="text-zinc-600">El script no produjo salida.</p>
            )}
          </>
        )}
      </div>
    </section>
  );
}
