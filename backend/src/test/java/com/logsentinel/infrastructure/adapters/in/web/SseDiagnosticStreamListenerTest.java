package com.logsentinel.infrastructure.adapters.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link SseDiagnosticStreamListener} (LOG-US3-BE-01). Pure Mockito —
 * mocks the {@link SseEmitter} directly, no real HTTP transport involved. Verifies the
 * mandatory project pattern: {@code emitter.complete()} always runs, even on error
 * (verify-clean-arch Check 7), to avoid orphaned Tomcat threads.
 */
@ExtendWith(MockitoExtension.class)
class SseDiagnosticStreamListenerTest {

    @Mock
    private SseEmitter emitter;

    @Test
    @DisplayName("should send each chunk as an SSE event carrying a chunk field")
    void should_send_each_chunk_as_sse_event() throws IOException {
        var listener = new SseDiagnosticStreamListener(emitter);

        listener.onChunk("Root cause: pool exhaustion");

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("should complete the emitter without error when the stream finishes successfully")
    void should_complete_emitter_when_no_error() {
        var listener = new SseDiagnosticStreamListener(emitter);

        listener.onComplete(null);

        verify(emitter, times(1)).complete();
        verify(emitter, never()).completeWithError(any());
    }

    @Test
    @DisplayName("should complete the emitter with error, and still call complete() in finally, when the stream fails")
    void should_complete_emitter_with_error_when_stream_fails() {
        var listener = new SseDiagnosticStreamListener(emitter);
        RuntimeException failure = new RuntimeException("LLM provider unavailable");

        listener.onComplete(failure);

        verify(emitter, times(1)).completeWithError(failure);
        verify(emitter, times(1)).complete();
    }

    @Test
    @DisplayName("should complete the emitter with error, in the finally block, when send() fails with IOException")
    void should_complete_with_error_when_send_fails() throws IOException {
        willThrow(new IOException("broken pipe")).given(emitter).send(any(SseEmitter.SseEventBuilder.class));
        var listener = new SseDiagnosticStreamListener(emitter);

        listener.onChunk("Root cause: pool exhaustion");

        verify(emitter, times(1)).completeWithError(any(IOException.class));
    }
}
