package com.logsentinel.application.service;

import com.logsentinel.application.ports.in.GetIncidentDetailUseCase;
import com.logsentinel.application.ports.out.IncidentDiagnosticRepository;
import com.logsentinel.application.ports.out.IncidentRepository;
import com.logsentinel.domain.exception.IncidentNotFoundException;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentDiagnostic;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Application service implementing {@link GetIncidentDetailUseCase} (LOG-US4-BE-03).
 * Read-only orchestration: looks up the incident and its persisted diagnostic history
 * via the two existing repository ports — no new persistence is introduced by this
 * ticket, it only composes {@link IncidentRepository} and
 * {@link IncidentDiagnosticRepository}, both already backing LOG-US3-BE-01 /
 * LOG-US4-BE-02.
 */
@Service
public class GetIncidentDetailService implements GetIncidentDetailUseCase {

    private final IncidentRepository incidentRepository;
    private final IncidentDiagnosticRepository incidentDiagnosticRepository;

    public GetIncidentDetailService(IncidentRepository incidentRepository,
                                     IncidentDiagnosticRepository incidentDiagnosticRepository) {
        this.incidentRepository = incidentRepository;
        this.incidentDiagnosticRepository = incidentDiagnosticRepository;
    }

    @Override
    public IncidentDetailResult execute(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));

        List<IncidentDiagnostic> analyses = incidentDiagnosticRepository.findByIncidentId(incidentId)
                .map(List::of)
                .orElseGet(List::of);

        return new IncidentDetailResult(incident, analyses);
    }
}
