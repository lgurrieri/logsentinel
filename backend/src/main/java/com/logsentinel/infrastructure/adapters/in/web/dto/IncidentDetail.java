package com.logsentinel.infrastructure.adapters.in.web.dto;

import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.Urgency;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Immutable response DTO for the consolidated detail of an incident, matching the
 * {@code IncidentDetail} schema of {@code docs/openapi: 3.0.yml}
 * ({@code Incident} {@code allOf} + {@code analyses: IncidentAnalysis[]}) — LOG-US4-BE-03.
 * Returned by {@code GET /incidents/{id}}.
 * <p>
 * KNOWN LIMITATION (pre-existing, not introduced by this ticket, non-blocking —
 * see {@code docs/deuda-tecnica.md}): the contract's {@code Incident} schema also
 * defines {@code updatedAt}, but no {@code Incident} domain model, JPA entity, or
 * migration in the codebase tracks it today (same gap already present in
 * {@code IncidentResponse} for {@code POST /incidents}, LOG-US1-BE-02B). Adding it
 * would touch the {@code Incident} domain constructor used across the whole codebase
 * (persistence adapter, multiple service tests) — out of scope for a read-only
 * detail endpoint ticket. The field is intentionally omitted here rather than
 * populated with a fabricated value.
 */
public record IncidentDetail(
        UUID id,
        String systemName,
        Urgency urgency,
        IncidentStatus status,
        OffsetDateTime createdAt,
        List<IncidentAnalysis> analyses
) {
}
