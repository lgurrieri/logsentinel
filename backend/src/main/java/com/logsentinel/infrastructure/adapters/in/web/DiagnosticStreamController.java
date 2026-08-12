package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.application.ports.in.StreamDiagnosticUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * REST controller streaming the AI-generated root-cause diagnostic of an incident via
 * Server-Sent Events (LOG-US3-BE-01).
 * <p>
 * Returns the {@link SseEmitter} immediately (non-blocking) and delegates the actual
 * work to {@link StreamDiagnosticUseCase#execute}, whose implementation runs
 * {@code @Async} on a dedicated worker thread — the Servlet request-handling thread is
 * released right after this method returns, never blocked waiting on the LLM.
 * <p>
 * The emitter's own timeout is injected from {@code spring.mvc.async.request-timeout}
 * (LOG-US3-BE-04) instead of a separate hardcoded constant, so it always stays aligned
 * with Spring MVC's global async request timeout — the two used to drift (a 60s emitter
 * timeout against an implicit 30s MVC default), which is irrelevant to correctness now
 * that {@code StreamDiagnosticService} persists the diagnostic independently of the SSE
 * connection lifecycle, but keeping them in sync avoids one more surprising discrepancy.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class DiagnosticStreamController {

    private final StreamDiagnosticUseCase streamDiagnosticUseCase;
    private final long sseTimeoutMillis;

    public DiagnosticStreamController(StreamDiagnosticUseCase streamDiagnosticUseCase,
                                       @Value("${spring.mvc.async.request-timeout}") long sseTimeoutMillis) {
        this.streamDiagnosticUseCase = streamDiagnosticUseCase;
        this.sseTimeoutMillis = sseTimeoutMillis;
    }

    @GetMapping(value = "/{id}/diagnostic/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDiagnostic(@PathVariable UUID id) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMillis);
        streamDiagnosticUseCase.execute(id, new SseDiagnosticStreamListener(emitter));
        return emitter;
    }
}

