import { useEffect, useId, useRef } from 'react';

interface ConfirmExecutionModalProps {
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Ventana de Confirmación Modal — doble aprobación (LOG-US4-FE-03).
 *
 * Al hacer clic en el CTA principal no se dispara el script de inmediato: este modal
 * fuerza una segunda confirmación explícita del operador antes de que
 * `useRemediationExecutor.confirmExecution` dispare la petición real. El foco de
 * teclado se mueve al botón "Cancelar" al montarse (opción no destructiva por
 * defecto), para que una tecla Enter accidental no dispare la ejecución.
 */
export function ConfirmExecutionModal({ onConfirm, onCancel }: ConfirmExecutionModalProps) {
  const titleId = useId();
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    cancelButtonRef.current?.focus();
  }, []);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      className="fixed inset-0 flex items-center justify-center bg-zinc-950/80 p-4"
    >
      <div className="flex flex-col gap-4 max-w-md w-full bg-zinc-900 border border-red-400 rounded-lg p-6">
        <h2 id={titleId} className="text-lg font-semibold text-zinc-100">
          Confirmación requerida
        </h2>
        <p className="text-zinc-100">
          ¿Confirmas la ejecución de este comando en el sistema de producción? Esta acción
          quedará registrada bajo tu firma de auditoría.
        </p>
        <div className="flex justify-end gap-4">
          <button
            ref={cancelButtonRef}
            type="button"
            onClick={onCancel}
            className="text-zinc-100 bg-zinc-700 rounded-lg px-4 py-2"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="text-zinc-950 bg-red-400 font-semibold rounded-lg px-4 py-2"
          >
            Confirmar Ejecución
          </button>
        </div>
      </div>
    </div>
  );
}
