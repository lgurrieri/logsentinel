package com.logsentinel.application.service;

import com.logsentinel.application.ports.in.CreateIncidentUseCase;
import com.logsentinel.application.ports.out.IncidentRepository;
import com.logsentinel.domain.model.Incident;
import org.springframework.stereotype.Service;

/**
 * Application service implementing the CreateIncidentUseCase.
 * Orchestrates the creation of a new incident via the repository port.
 */
@Service
public class CreateIncidentService implements CreateIncidentUseCase {

    private final IncidentRepository incidentRepository;

    public CreateIncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Override
    public Incident execute(CreateIncidentCommand command) {
        Incident incident = Incident.createNew(
                command.systemName(),
                command.urgency(),
                command.rawLogs()
        );
        return incidentRepository.save(incident);
    }
}
