package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.application.ports.in.CreateIncidentUseCase;
import com.logsentinel.application.ports.in.CreateIncidentUseCase.CreateIncidentCommand;
import com.logsentinel.application.ports.in.GetIncidentDetailUseCase;
import com.logsentinel.application.ports.in.GetIncidentDetailUseCase.IncidentDetailResult;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentDiagnostic;
import com.logsentinel.infrastructure.adapters.in.web.dto.CreateIncidentRequest;
import com.logsentinel.infrastructure.adapters.in.web.dto.IncidentAnalysis;
import com.logsentinel.infrastructure.adapters.in.web.dto.IncidentDetail;
import com.logsentinel.infrastructure.adapters.in.web.dto.IncidentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for incident ingestion and lookup.
 * Validates incoming requests via JSR-380 annotations and delegates to the use cases.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    /**
     * Hardcoded placeholder for {@code IncidentAnalysis.tokensUsed} (LOG-US4-BE-03,
     * non-blocking, documented in {@code IncidentAnalysis} Javadoc and
     * {@code docs/deuda-tecnica.md}): no component in the current AI pipeline
     * captures LLM token usage yet.
     */
    private static final int TOKENS_USED_NOT_TRACKED_PLACEHOLDER = 0;

    private final CreateIncidentUseCase createIncidentUseCase;
    private final GetIncidentDetailUseCase getIncidentDetailUseCase;

    public IncidentController(CreateIncidentUseCase createIncidentUseCase,
                               GetIncidentDetailUseCase getIncidentDetailUseCase) {
        this.createIncidentUseCase = createIncidentUseCase;
        this.getIncidentDetailUseCase = getIncidentDetailUseCase;
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

    @GetMapping("/{id}")
    public IncidentDetail getDetail(@PathVariable UUID id) {
        IncidentDetailResult result = getIncidentDetailUseCase.execute(id);
        return toDetail(result);
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

    private IncidentDetail toDetail(IncidentDetailResult result) {
        Incident incident = result.incident();
        return new IncidentDetail(
                incident.getId(),
                incident.getSystemName(),
                incident.getUrgency(),
                incident.getStatus(),
                incident.getCreatedAt(),
                result.analyses().stream()
                        .map(diagnostic -> toAnalysis(diagnostic, incident.getRawLogs()))
                        .toList()
        );
    }

    private IncidentAnalysis toAnalysis(IncidentDiagnostic diagnostic, String rawLogSnapshot) {
        return new IncidentAnalysis(
                diagnostic.getId(),
                rawLogSnapshot,
                diagnostic.getDiagnosticText(),
                diagnostic.getSuggestedScript(),
                TOKENS_USED_NOT_TRACKED_PLACEHOLDER,
                diagnostic.getCreatedAt()
        );
    }
}
