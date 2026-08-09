package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.application.ports.in.CreateIncidentUseCase;
import com.logsentinel.application.ports.in.CreateIncidentUseCase.CreateIncidentCommand;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.infrastructure.adapters.in.web.dto.CreateIncidentRequest;
import com.logsentinel.infrastructure.adapters.in.web.dto.IncidentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for incident ingestion.
 * Validates incoming requests via JSR-380 annotations and delegates to the use case.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final CreateIncidentUseCase createIncidentUseCase;

    public IncidentController(CreateIncidentUseCase createIncidentUseCase) {
        this.createIncidentUseCase = createIncidentUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse create(@Valid @RequestBody CreateIncidentRequest request) {
        CreateIncidentCommand command = new CreateIncidentCommand(
                request.systemName(),
                request.urgency(),
                request.rawLogSnapshot()
        );
        Incident incident = createIncidentUseCase.execute(command);
        return toResponse(incident);
    }

    private IncidentResponse toResponse(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getSystemName(),
                incident.getUrgency(),
                incident.getStatus(),
                incident.getCreatedAt()
        );
    }
}
