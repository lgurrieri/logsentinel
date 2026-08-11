import { useEffect, useRef, useState } from 'react';
import { useDiagnosticStream } from '../hooks/useDiagnosticStream';
import { useDiagnosticStreamConnection } from '../hooks/useDiagnosticStreamConnection';
import { sanitizeMarkdown } from '../utils/sanitizeMarkdown';
import type { DiagnosticStreamStatus } from '../types/diagnosticStream.types';

interface DiagnosticTerminalProps {
  /** Identificador del incidente cuyo diagnóstico se transmite. `null` = sin conectar aún. */
  incidentId: string | null;
}

/** Margen de tolerancia (px) para considerar que el usuario sigue "anclado" al fondo. */
const SCROLL_BOTTOM_THRESHOLD_PX = 24;

const STATUS_LABEL: Record<DiagnosticStreamStatus, string> = {
  IDLE: 'Esperando análisis...',
  CONNECTING: 'Conectando con el agente SRE...',
  STREAMING: 'Analizando incidente...',
  RECONNECTING: 'Reconectando...',
  COMPLETED: 'Diagnóstico completado',
  STREAM_FAILED: 'No se pudo completar el diagnóstico',
};

const STATUS_INDICATOR_CLASS: Record<DiagnosticStreamStatus, string> = {
  IDLE: 'bg-zinc-500',
  CONNECTING: 'bg-zinc-500 animate-pulse',
  STREAMING: 'bg-green-400 animate-pulse',
  RECONNECTING: 'bg-amber-400 animate-pulse',
  COMPLETED: 'bg-blue-400',
  STREAM_FAILED: 'bg-red-400',
};

/**
 * Terminal Consola interactiva del diagnóstico de IA (LOG-US3-FE-03).
 *
 * Se conecta al stream SSE vía `useDiagnosticStreamConnection`, renderiza el buffer
 * Markdown acumulado como HTML saneado (`sanitizeMarkdown` = `marked` + `DOMPurify`),
 * mantiene un cursor parpadeante mientras hay streaming activo, e implementa
 * auto-scroll inteligente: solo empuja el scroll al fondo si el usuario ya estaba
 * anclado ahí; si sube manualmente, se desactiva y aparece un botón flotante.
 */
export function DiagnosticTerminal({ incidentId }: DiagnosticTerminalProps) {
  const { state } = useDiagnosticStream();
  useDiagnosticStreamConnection(incidentId);

  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const [isPinnedToBottom, setIsPinnedToBottom] = useState(true);

  function handleScroll() {
    const el = scrollContainerRef.current;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    setIsPinnedToBottom(distanceFromBottom <= SCROLL_BOTTOM_THRESHOLD_PX);
  }

  // Auto-scroll inteligente: solo empuja el scroll al fondo si el usuario ya estaba
  // anclado ahí. Si el SRE subió manualmente la barra, no se interrumpe su lectura.
  useEffect(() => {
    const el = scrollContainerRef.current;
    if (!el || !isPinnedToBottom) return;
    el.scrollTop = el.scrollHeight;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.diagnosticBuffer]);

  function scrollToBottom() {
    const el = scrollContainerRef.current;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
    setIsPinnedToBottom(true);
  }

  const showCursor = state.status === 'STREAMING';
  const showNewLinesButton = !isPinnedToBottom && state.diagnosticBuffer.length > 0;

  return (
    <section
      aria-label="Terminal de diagnóstico"
      aria-live="polite"
      aria-busy={state.status === 'STREAMING'}
      // Altura fija de antemano para prevenir Cumulative Layout Shift (CLS).
      className="relative flex flex-col h-96 min-h-96 bg-zinc-950 border border-zinc-700 rounded-lg overflow-hidden"
    >
      <div className="flex items-center gap-2 px-4 py-2 bg-zinc-900 border-b border-zinc-700">
        <span className={`h-2 w-2 rounded-full ${STATUS_INDICATOR_CLASS[state.status]}`} />
        <span className="text-xs text-zinc-400 font-mono">{STATUS_LABEL[state.status]}</span>
      </div>

      <div
        ref={scrollContainerRef}
        onScroll={handleScroll}
        data-testid="diagnostic-terminal-scroll"
        className="flex-1 overflow-y-auto p-4 font-mono text-sm text-green-400 whitespace-pre-wrap break-words"
      >
        {state.diagnosticBuffer ? (
          <span dangerouslySetInnerHTML={{ __html: sanitizeMarkdown(state.diagnosticBuffer) }} />
        ) : (
          <span className="text-zinc-600">El diagnóstico aparecerá aquí token a token...</span>
        )}
        {showCursor && (
          <span data-testid="terminal-cursor" className="terminal-cursor" aria-hidden="true">
            _
          </span>
        )}
      </div>

      {showNewLinesButton && (
        <button
          type="button"
          onClick={scrollToBottom}
          className="absolute bottom-4 right-4 bg-blue-400 text-zinc-950 text-xs font-semibold rounded-lg px-3 py-2 shadow"
        >
          Nuevas líneas disponibles abajo ↓
        </button>
      )}

      {state.status === 'STREAM_FAILED' && state.errorMessage && (
        <div
          role="alert"
          className="px-4 py-2 bg-red-950 border-t border-red-800 text-red-400 text-xs font-mono"
        >
          {state.errorMessage}
        </div>
      )}
    </section>
  );
}
