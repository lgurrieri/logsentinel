package com.logsentinel.infrastructure.adapters.in.web.dto;

import com.logsentinel.domain.model.RemediationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable response DTO for a remediation action's audit record, exposed via the
 * REST API (LOG-US4-BE-02), matching the {@code RemediationAction} schema of
 * {@code docs/openapi: 3.0.yml}.
 */
public record RemediationActionResponse(
        UUID id,
        String generatedScript,
        RemediationStatus executionStatus,
        OffsetDateTime executedAt,
        String executionLog
) {
}
