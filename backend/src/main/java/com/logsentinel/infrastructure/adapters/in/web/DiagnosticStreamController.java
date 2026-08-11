package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.application.ports.in.StreamDiagnosticUseCase;
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
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class DiagnosticStreamController {

    private static final long SSE_TIMEOUT_MILLIS = 60_000L;

    private final StreamDiagnosticUseCase streamDiagnosticUseCase;

    public DiagnosticStreamController(StreamDiagnosticUseCase streamDiagnosticUseCase) {
        this.streamDiagnosticUseCase = streamDiagnosticUseCase;
    }

    @GetMapping(value = "/{id}/diagnostic/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDiagnostic(@PathVariable UUID id) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        streamDiagnosticUseCase.execute(id, new SseDiagnosticStreamListener(emitter));
        return emitter;
    }
}
