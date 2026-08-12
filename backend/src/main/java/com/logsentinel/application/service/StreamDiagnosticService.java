package com.logsentinel.application.service;

import com.logsentinel.application.ports.in.StreamDiagnosticUseCase;
import com.logsentinel.application.ports.out.DiagnosticChatPort;
import com.logsentinel.application.ports.out.DiagnosticStreamListener;
import com.logsentinel.application.ports.out.IncidentDiagnosticRepository;
import com.logsentinel.application.ports.out.IncidentRepository;
import com.logsentinel.application.ports.out.RunbookSearchPort;
import com.logsentinel.domain.exception.IncidentNotFoundException;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentDiagnostic;
import com.logsentinel.domain.model.RunbookChunk;
import com.logsentinel.domain.service.SuggestedScriptExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Application service implementing {@link StreamDiagnosticUseCase} (LOG-US3-BE-01).
 * Orchestrates the RAG diagnostic pipeline for a single incident: looks up the
 * incident, retrieves the most relevant runbooks ({@link RunbookSearchPort},
 * LOG-US2-BE-02), augments the LLM prompt with that context, and streams the
 * diagnosis fragment by fragment to the caller-supplied {@link DiagnosticStreamListener}.
 * <p>
 * Annotated {@code @Async} per the project's mandatory SSE pattern (see
 * {@code .github/copilot-instructions.md}, section "SSE") — {@code @EnableAsync} is
 * already active in {@code LogSentinelApplication}, so this method runs on the
 * dedicated task executor pool configured under {@code spring.task.execution.pool}
 * instead of blocking the HTTP request thread.
 * <p>
 * Every code path — success, incident not found, or chat provider failure — notifies
 * {@code listener.onComplete(...)} exactly once via a {@code finally} block, so the
 * infrastructure SSE adapter can reliably close the emitter (verify-clean-arch Check 7).
 * <p>
 * When the stream completes successfully, the full concatenated diagnostic text is
 * frozen and persisted via {@link IncidentDiagnosticRepository} (LOG-US3-DB-02) before
 * {@code onComplete} is notified. A persistence failure is logged but never surfaces to
 * the listener — by that point the diagnostic has already been fully streamed to the
 * client, so failing the SSE channel after the fact would not undo already-sent bytes.
 * <p>
 * Before persisting, {@link SuggestedScriptExtractor} (LOG-US3-DB-02B) runs exactly
 * once over the fully-consolidated diagnostic text — never fragment by fragment — to
 * authoritatively derive {@code suggestedScript}, so a later remediation execution flow
 * never depends on the client resending or re-parsing AI-generated code.
 * <p>
 * <b>Resilience to a dead {@code listener}/SSE connection (LOG-US3-BE-04):</b> forwarding
 * a chunk to {@code listener} is treated as best-effort. If the SSE client already
 * disconnected or the request's async timeout already fired, {@code listener.onChunk}
 * (and {@code listener.onComplete}) can throw (e.g. {@code IllegalStateException} from an
 * already-completed {@code SseEmitter}). That failure is caught and logged right at the
 * forwarding call site — it is NEVER allowed to propagate back into
 * {@link DiagnosticChatPort#streamDiagnosis}, because doing so would abort the
 * in-progress chat stream consumption itself (the underlying {@code Stream.forEach} in
 * {@code SpringAiDiagnosticChatAdapter} stops pulling further chunks the moment the
 * fragment consumer throws). This is what previously caused a fully-generated diagnostic
 * to be silently dropped in production: the {@code SseEmitter} timed out mid-generation,
 * the next {@code emitter.send(...)} threw, that exception unwound all the way to
 * {@code execute()}'s outer {@code catch}, and {@code persistDiagnostic} was skipped
 * (gated on {@code error == null}). By isolating listener failures here, diagnostic
 * generation and persistence always run to completion on this method's dedicated
 * {@code @Async} thread, fully independent of the SSE emitter's/HTTP request's lifecycle.
 */
@Service
public class StreamDiagnosticService implements StreamDiagnosticUseCase {

    private static final Logger log = LoggerFactory.getLogger(StreamDiagnosticService.class);

    private final IncidentRepository incidentRepository;
    private final RunbookSearchPort runbookSearchPort;
    private final DiagnosticChatPort diagnosticChatPort;
    private final IncidentDiagnosticRepository incidentDiagnosticRepository;
    private final SuggestedScriptExtractor suggestedScriptExtractor;

    public StreamDiagnosticService(IncidentRepository incidentRepository,
                                    RunbookSearchPort runbookSearchPort,
                                    DiagnosticChatPort diagnosticChatPort,
                                    IncidentDiagnosticRepository incidentDiagnosticRepository) {
        this.incidentRepository = incidentRepository;
        this.runbookSearchPort = runbookSearchPort;
        this.diagnosticChatPort = diagnosticChatPort;
        this.incidentDiagnosticRepository = incidentDiagnosticRepository;
        this.suggestedScriptExtractor = new SuggestedScriptExtractor();
    }

    @Async
    @Override
    public void execute(UUID incidentId, DiagnosticStreamListener listener) {
        Throwable error = null;
        StringBuilder diagnosticText = new StringBuilder();
        AtomicBoolean listenerDisconnected = new AtomicBoolean(false);
        try {
            Incident incident = incidentRepository.findById(incidentId)
                    .orElseThrow(() -> new IncidentNotFoundException(incidentId));
            List<RunbookChunk> runbooks = runbookSearchPort.findSimilarRunbooks(incident.getRawLogs());

            diagnosticChatPort.streamDiagnosis(
                    buildSystemPrompt(runbooks),
                    buildUserPrompt(incident),
                    fragment -> {
                        diagnosticText.append(fragment);
                        forwardChunkSafely(listener, fragment, incidentId, listenerDisconnected);
                    });
        } catch (Exception e) {
            log.error("Diagnostic stream failed", Map.of(
                    "incidentId", String.valueOf(incidentId),
                    "cause", String.valueOf(e.getMessage())
            ));
            error = e;
        } finally {
            if (error == null) {
                persistDiagnostic(incidentId, diagnosticText.toString());
            }
            notifyCompletionSafely(listener, error, incidentId);
        }
    }

    /**
     * Forwards a single fragment to {@code listener}, treating the notification as
     * best-effort (LOG-US3-BE-04). Once the listener fails once (dead SSE emitter —
     * client disconnected, or the async request already timed out), it is marked
     * {@code listenerDisconnected} and silently skipped for every subsequent fragment:
     * the LLM keeps generating on the server regardless, so there is no point retrying a
     * connection that is already gone, and doing so would otherwise flood the logs with
     * one warning per remaining chunk.
     * <p>
     * Critically, any exception thrown here is swallowed and never rethrown: letting it
     * propagate back into the {@code Consumer<String>} passed to
     * {@link DiagnosticChatPort#streamDiagnosis} would abort the chat stream consumption
     * itself, not just the forwarding to the client.
     */
    private void forwardChunkSafely(DiagnosticStreamListener listener, String fragment, UUID incidentId,
                                     AtomicBoolean listenerDisconnected) {
        if (listenerDisconnected.get()) {
            return;
        }
        try {
            listener.onChunk(fragment);
        } catch (Exception e) {
            listenerDisconnected.set(true);
            log.warn("SSE listener disconnected mid-stream; diagnostic generation and persistence "
                    + "continue independently in the background (LOG-US3-BE-04)", Map.of(
                    "incidentId", String.valueOf(incidentId),
                    "cause", String.valueOf(e.getMessage())
            ));
        }
    }

    /**
     * Notifies {@code listener.onComplete(error)}, best-effort (LOG-US3-BE-04). By the
     * time this runs, {@code persistDiagnostic} has already completed (see the
     * {@code finally} block in {@link #execute}) — so a failure notifying a
     * possibly-already-dead listener must never be allowed to look like a persistence
     * failure, nor propagate out of this {@code @Async} method uncaught.
     */
    private void notifyCompletionSafely(DiagnosticStreamListener listener, Throwable error, UUID incidentId) {
        try {
            listener.onComplete(error);
        } catch (Exception e) {
            log.warn("Failed to notify the SSE listener of stream completion; the diagnostic was already "
                    + "persisted independently beforehand (LOG-US3-BE-04)", Map.of(
                    "incidentId", String.valueOf(incidentId),
                    "cause", String.valueOf(e.getMessage())
            ));
        }
    }

    private void persistDiagnostic(UUID incidentId, String diagnosticText) {
        try {
            String suggestedScript = suggestedScriptExtractor.extract(diagnosticText);
            incidentDiagnosticRepository.save(
                    IncidentDiagnostic.createNew(incidentId, diagnosticText, suggestedScript));
        } catch (Exception e) {
            log.error("Failed to persist consolidated incident diagnostic", Map.of(
                    "incidentId", String.valueOf(incidentId),
                    "cause", String.valueOf(e.getMessage())
            ));
        }
    }

    private String buildSystemPrompt(List<RunbookChunk> runbooks) {
        String runbookContext = runbooks.stream()
                .map(RunbookChunk::content)
                .collect(Collectors.joining("\n---\n"));
        return """
                Eres un ingeniero SRE experto. Diagnostica la causa raiz del incidente \
                basandote en los siguientes runbooks disponibles. No inventes soluciones \
                fuera de este contexto.

                RUNBOOKS DISPONIBLES:
                %s
                """.formatted(runbookContext);
    }

    private String buildUserPrompt(Incident incident) {
        return """
                Analiza este incidente:
                Sistema: %s
                Urgencia: %s
                Log: %s
                """.formatted(incident.getSystemName(), incident.getUrgency(), incident.getRawLogs());
    }
}
