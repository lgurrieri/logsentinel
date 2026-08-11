package com.logsentinel.application.ports.out;

/**
 * Driven port (SPI) that the {@code StreamDiagnosticUseCase} (LOG-US3-BE-01) notifies
 * as the diagnostic text streams in. Pure Java interface — infrastructure adapts this
 * to whatever transport it needs (Server-Sent Events via {@code SseEmitter}), so the
 * application layer never depends on Spring MVC's streaming types.
 */
public interface DiagnosticStreamListener {

    /**
     * Invoked once per text fragment, in the order received from the LLM.
     */
    void onChunk(String textFragment);

    /**
     * Invoked exactly once, when the stream terminates — either successfully
     * ({@code error == null}) or because of a failure ({@code error != null}).
     */
    void onComplete(Throwable error);
}
