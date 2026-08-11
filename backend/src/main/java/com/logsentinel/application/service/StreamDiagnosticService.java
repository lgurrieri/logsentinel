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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 */
@Service
public class StreamDiagnosticService implements StreamDiagnosticUseCase {

    private static final Logger log = LoggerFactory.getLogger(StreamDiagnosticService.class);

    private final IncidentRepository incidentRepository;
    private final RunbookSearchPort runbookSearchPort;
    private final DiagnosticChatPort diagnosticChatPort;
    private final IncidentDiagnosticRepository incidentDiagnosticRepository;

    public StreamDiagnosticService(IncidentRepository incidentRepository,
                                    RunbookSearchPort runbookSearchPort,
                                    DiagnosticChatPort diagnosticChatPort,
                                    IncidentDiagnosticRepository incidentDiagnosticRepository) {
        this.incidentRepository = incidentRepository;
        this.runbookSearchPort = runbookSearchPort;
        this.diagnosticChatPort = diagnosticChatPort;
        this.incidentDiagnosticRepository = incidentDiagnosticRepository;
    }

    @Async
    @Override
    public void execute(UUID incidentId, DiagnosticStreamListener listener) {
        Throwable error = null;
        StringBuilder diagnosticText = new StringBuilder();
        try {
            Incident incident = incidentRepository.findById(incidentId)
                    .orElseThrow(() -> new IncidentNotFoundException(incidentId));
            List<RunbookChunk> runbooks = runbookSearchPort.findSimilarRunbooks(incident.getRawLogs());

            diagnosticChatPort.streamDiagnosis(
                    buildSystemPrompt(runbooks),
                    buildUserPrompt(incident),
                    fragment -> {
                        diagnosticText.append(fragment);
                        listener.onChunk(fragment);
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
            listener.onComplete(error);
        }
    }

    private void persistDiagnostic(UUID incidentId, String diagnosticText) {
        try {
            incidentDiagnosticRepository.save(IncidentDiagnostic.createNew(incidentId, diagnosticText));
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
