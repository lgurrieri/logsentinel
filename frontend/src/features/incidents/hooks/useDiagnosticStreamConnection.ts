import { useEffect, useRef } from 'react';
import { useDiagnosticStream } from './useDiagnosticStream';
import type { DiagnosticChunkPayload } from '../types/diagnosticStream.types';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

const MAX_RETRIES = 3;
const RETRY_BASE_MS = 1000;

const GENERIC_STREAM_FAILED_MESSAGE =
  'No se pudo restablecer la conexión con el agente de diagnóstico.';

/**
 * Conecta el `EventSource` nativo al endpoint SSE del diagnóstico de IA
 * (`GET /api/v1/incidents/{id}/diagnostic/stream`, ver `docs/openapi: 3.0.yml`) y
 * alimenta `DiagnosticStreamContext` con los chunks recibidos.
 *
 * ### Por qué `onerror` se resuelve con una heurística
 * El backend (`SseDiagnosticStreamListener`) únicamente emite eventos
 * `data: {"chunk": "..."}` y cierra la conexión con `emitter.complete()` al terminar —
 * el contrato no define un evento SSE explícito de cierre exitoso (`event: complete`
 * o similar). Por diseño de `EventSource`, tanto un cierre normal del servidor como una
 * caída real de red disparan `onerror` de forma indistinguible.
 *
 * Se resuelve así: si ya se recibió al menos un `chunk` en la conexión actual antes de
 * `onerror`, se asume que el LLM terminó de emitir el diagnóstico y el servidor cerró
 * limpiamente ⇒ `STREAM_COMPLETED` (sin reintentar). Si NO se recibió ningún chunk, se
 * asume una falla de conectividad real ⇒ reintento con backoff exponencial
 * (1s, 2s, 4s) hasta `MAX_RETRIES` veces antes de marcar `STREAM_FAILED`.
 */
export function useDiagnosticStreamConnection(incidentId: string | null) {
  const { dispatch } = useDiagnosticStream();
  const retriesRef = useRef(0);

  useEffect(() => {
    if (!incidentId) return;

    let cancelled = false;
    let retryTimeoutId: ReturnType<typeof setTimeout> | undefined;
    let activeSource: EventSource | null = null;
    let hasReceivedChunk = false;

    function connect() {
      const source = new EventSource(
        `${API_BASE}/api/v1/incidents/${incidentId}/diagnostic/stream`,
      );
      activeSource = source;

      source.onmessage = (event: MessageEvent<string>) => {
        hasReceivedChunk = true;
        retriesRef.current = 0;
        try {
          const parsed = JSON.parse(event.data) as DiagnosticChunkPayload;
          dispatch({ type: 'RECEIVE_CHUNK', payload: parsed.chunk ?? event.data });
        } catch {
          // El backend envió texto plano en vez de JSON — se usa tal cual.
          dispatch({ type: 'RECEIVE_CHUNK', payload: event.data });
        }
      };

      source.onerror = () => {
        source.close();
        if (cancelled) return;

        if (hasReceivedChunk) {
          dispatch({ type: 'STREAM_COMPLETED' });
          return;
        }

        if (retriesRef.current < MAX_RETRIES) {
          dispatch({ type: 'CONNECTION_LOST' });
          const delay = RETRY_BASE_MS * 2 ** retriesRef.current;
          retriesRef.current += 1;
          retryTimeoutId = setTimeout(connect, delay);
        } else {
          dispatch({ type: 'STREAM_FAILED', payload: GENERIC_STREAM_FAILED_MESSAGE });
        }
      };
    }

    retriesRef.current = 0;
    dispatch({ type: 'START_STREAM' });
    connect();

    return () => {
      cancelled = true;
      activeSource?.close();
      if (retryTimeoutId) clearTimeout(retryTimeoutId);
      retriesRef.current = 0;
    };
  }, [incidentId, dispatch]);
}
