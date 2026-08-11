package com.logsentinel.application.ports.in;

import com.logsentinel.application.ports.out.DiagnosticStreamListener;

import java.util.UUID;

/**
 * Driving port (use case) for streaming the AI-generated root-cause diagnostic of a
 * given incident (LOG-US3-BE-01). Pure Java interface — no framework dependency.
 */
public interface StreamDiagnosticUseCase {

    /**
     * Streams the diagnostic for the given incident, notifying {@code listener} as
     * fragments arrive. Implementations run asynchronously (see {@code @Async}) so
     * this method MUST NOT be called expecting a synchronous/blocking return; the
     * outcome is only observable through the listener callbacks.
     *
     * @param incidentId the incident to diagnose
     * @param listener   notified with each text fragment and, exactly once, on completion
     */
    void execute(UUID incidentId, DiagnosticStreamListener listener);
}
