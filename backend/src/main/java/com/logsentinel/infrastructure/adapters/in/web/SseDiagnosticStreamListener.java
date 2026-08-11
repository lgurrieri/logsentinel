package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.application.ports.out.DiagnosticStreamListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * Infrastructure-only adapter that implements {@link DiagnosticStreamListener} by
 * translating each callback into an operation on a Spring MVC {@link SseEmitter}. This
 * is the ONLY class that touches the emitter directly, keeping the application layer
 * free of any web/Servlet framework dependency (LOG-US3-BE-01).
 * <p>
 * {@code emitter.complete()} always runs inside a {@code finally} block in
 * {@link #onComplete(Throwable)} — the mandatory project pattern (see
 * {@code .github/copilot-instructions.md}, section "SSE") that prevents orphaned
 * Tomcat threads (verify-clean-arch Check 7).
 */
final class SseDiagnosticStreamListener implements DiagnosticStreamListener {

    private final SseEmitter emitter;

    SseDiagnosticStreamListener(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onChunk(String textFragment) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("chunk", textFragment)));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    @Override
    public void onComplete(Throwable error) {
        try {
            if (error != null) {
                emitter.completeWithError(error);
            }
        } finally {
            emitter.complete();
        }
    }
}
