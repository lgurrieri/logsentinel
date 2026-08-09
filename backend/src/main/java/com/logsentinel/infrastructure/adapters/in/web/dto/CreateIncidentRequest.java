package com.logsentinel.infrastructure.adapters.in.web.dto;

import com.logsentinel.domain.model.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Immutable request DTO for creating an incident.
 * JSR-380 annotations provide validation at the controller level.
 */
public record CreateIncidentRequest(
        @NotBlank(message = "systemName must not be blank")
        String systemName,

        @NotNull(message = "urgency must not be null")
        Urgency urgency,

        @NotBlank(message = "rawLogSnapshot must not be blank")
        @Size(min = 10, message = "rawLogSnapshot must be at least 10 characters")
        String rawLogSnapshot
) {
}
