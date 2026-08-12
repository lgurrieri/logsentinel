package com.logsentinel.infrastructure.adapters.in.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable response DTO for a single AI-generated diagnostic analysis of an
 * incident, matching the {@code IncidentAnalysis} schema of
 * {@code docs/openapi: 3.0.yml} (LOG-US4-BE-03). Sourced from the persisted
 * {@code IncidentDiagnostic} entity (LOG-US3-DB-02 / LOG-US3-DB-02B).
 * <p>
 * {@code rawLogSnapshot} is derived from the parent {@code Incident.rawLogs}: since
 * {@code incident_diagnostics} is enforced one-to-one with {@code incidents}
 * (LOG-US3-DB-02), the raw log snapshot this diagnostic analyzed is always exactly
 * the incident's own snapshot — no separate copy is persisted per diagnostic.
 * <p>
 * KNOWN LIMITATION (LOG-US4-BE-03, non-blocking, surfaced for the human ledger —
 * see {@code docs/deuda-tecnica.md}): {@code tokensUsed} is a hardcoded placeholder
 * {@code 0}. Nothing in the current AI pipeline captures LLM token usage —
 * {@code DiagnosticChatPort#streamDiagnosis} only forwards content fragments
 * ({@code .stream().content()}), never the underlying {@code ChatResponse}/
 * {@code Usage} metadata Spring AI exposes. Wiring a real value requires extending
 * {@code IncidentDiagnostic}/{@code incident_diagnostics} to persist it, which is
 * out of scope for this read-only endpoint ticket.
 */
public record IncidentAnalysis(
        UUID id,
        String rawLogSnapshot,
        String diagnosticOutput,
        String suggestedScript,
        Integer tokensUsed,
        OffsetDateTime createdAt
) {
}
