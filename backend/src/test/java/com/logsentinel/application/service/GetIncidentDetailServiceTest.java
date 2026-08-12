package com.logsentinel.application.service;

import com.logsentinel.application.ports.in.GetIncidentDetailUseCase.IncidentDetailResult;
import com.logsentinel.application.ports.out.IncidentDiagnosticRepository;
import com.logsentinel.application.ports.out.IncidentRepository;
import com.logsentinel.domain.exception.IncidentNotFoundException;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentDiagnostic;
import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * Unit test for {@link GetIncidentDetailService} (LOG-US4-BE-03). Pure Mockito — no
 * Spring context. Verifies the read-only orchestration logic: incident lookup,
 * diagnostic history retrieval, and the "not found" failure path.
 */
@ExtendWith(MockitoExtension.class)
class GetIncidentDetailServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentDiagnosticRepository incidentDiagnosticRepository;

    @InjectMocks
    private GetIncidentDetailService getIncidentDetailService;

    @Test
    @DisplayName("should return the incident with its persisted diagnostic when one exists")
    void should_return_incident_with_diagnostic_when_one_exists() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "payment-gw", Urgency.CRITICAL,
                "ERROR: connection pool exhausted", IncidentStatus.OPEN, OffsetDateTime.now());
        IncidentDiagnostic diagnostic = new IncidentDiagnostic(UUID.randomUUID(), incidentId,
                "Root cause: pool exhaustion.", "systemctl restart payment-gw", OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(incidentDiagnosticRepository.findByIncidentId(incidentId)).willReturn(Optional.of(diagnostic));

        IncidentDetailResult result = getIncidentDetailService.execute(incidentId);

        assertThat(result.incident()).isEqualTo(incident);
        assertThat(result.analyses()).containsExactly(diagnostic);
    }

    @Test
    @DisplayName("should return the incident with an empty analyses list when no diagnostic was ever persisted")
    void should_return_incident_with_empty_analyses_when_no_diagnostic_exists() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "auth-svc", Urgency.HIGH,
                "FATAL: token validation failed", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(incidentDiagnosticRepository.findByIncidentId(incidentId)).willReturn(Optional.empty());

        IncidentDetailResult result = getIncidentDetailService.execute(incidentId);

        assertThat(result.incident()).isEqualTo(incident);
        assertThat(result.analyses()).isEmpty();
    }

    @Test
    @DisplayName("should throw IncidentNotFoundException when the incident does not exist")
    void should_throw_incident_not_found_exception_when_incident_does_not_exist() {
        UUID incidentId = UUID.randomUUID();
        given(incidentRepository.findById(incidentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> getIncidentDetailService.execute(incidentId))
                .isInstanceOf(IncidentNotFoundException.class);
    }
}
