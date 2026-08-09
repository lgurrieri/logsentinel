package com.logsentinel.infrastructure.adapters.in.web.dto;

import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.Urgency;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable response DTO for incident data exposed via the REST API.
 */
public record IncidentResponse(
        UUID id,
        String systemName,
        Urgency urgency,
        IncidentStatus status,
        OffsetDateTime createdAt
) {
}
