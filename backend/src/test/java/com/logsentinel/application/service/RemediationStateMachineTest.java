package com.logsentinel.application.service;

import com.logsentinel.application.ports.out.IncidentRepository;
import com.logsentinel.application.ports.out.RemediationActionRepository;
import com.logsentinel.domain.exception.IncidentNotFoundException;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.RemediationAction;
import com.logsentinel.domain.model.RemediationStatus;
import com.logsentinel.domain.model.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link RemediationStateMachine} (LOG-US4-BE-02) — the two
 * independent, sequential {@code Propagation.REQUIRES_NEW} transaction phases
 * (Transaction A: commit EXECUTING; Transaction B: commit closure) described by
 * the ticket. Pure Mockito — no Spring context — verifies the orchestration
 * logic of each phase in isolation. Real transactional durability (that
 * Transaction A survives even if Transaction B or the surrounding flow later
 * fails) is proven separately by {@code RemediationStateMachineIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class RemediationStateMachineTest {

    @Mock
    private RemediationActionRepository remediationActionRepository;

    @Mock
    private IncidentRepository incidentRepository;

    private RemediationStateMachine stateMachine;

    private final UUID incidentId = UUID.randomUUID();

    @Test
    @DisplayName("should persist a new remediation action in EXECUTING status when the incident exists")
    void should_persist_new_remediation_action_in_executing_status_when_incident_exists() {
        stateMachine = new RemediationStateMachine(remediationActionRepository, incidentRepository);
        Incident existingIncident = new Incident(incidentId, "payment-gw", Urgency.CRITICAL,
                "ERROR: pool exhausted", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(existingIncident));
        given(remediationActionRepository.save(any())).willAnswer(invocation -> {
            RemediationAction submitted = invocation.getArgument(0);
            return new RemediationAction(UUID.randomUUID(), submitted.getIncidentId(), submitted.getGeneratedScript(),
                    submitted.getExecutionStatus(), submitted.getExecutionLog(), submitted.getExecutedAt(),
                    OffsetDateTime.now());
        });

        RemediationAction result = stateMachine.commitExecuting(incidentId, "echo hello-sandbox");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getIncidentId()).isEqualTo(incidentId);
        assertThat(result.getExecutionStatus()).isEqualTo(RemediationStatus.EXECUTING);
        assertThat(result.getExecutionLog()).isNull();
        assertThat(result.getExecutedAt()).isNull();

        ArgumentCaptor<RemediationAction> captor = ArgumentCaptor.forClass(RemediationAction.class);
        verify(remediationActionRepository).save(captor.capture());
        assertThat(captor.getValue().getGeneratedScript()).isEqualTo("echo hello-sandbox");
        assertThat(captor.getValue().getExecutionStatus()).isEqualTo(RemediationStatus.EXECUTING);
    }

    @Test
    @DisplayName("should throw IncidentNotFoundException and never persist a remediation action when the incident does not exist")
    void should_throw_when_incident_does_not_exist() {
        stateMachine = new RemediationStateMachine(remediationActionRepository, incidentRepository);
        given(incidentRepository.findById(incidentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> stateMachine.commitExecuting(incidentId, "echo hello-sandbox"))
                .isInstanceOf(IncidentNotFoundException.class);

        verify(remediationActionRepository, never()).save(any());
    }

    @Test
    @DisplayName("should close with SUCCESS status and resolve the incident when the exit code is zero")
    void should_close_with_success_and_resolve_incident_when_exit_code_zero() {
        stateMachine = new RemediationStateMachine(remediationActionRepository, incidentRepository);
        RemediationAction executing = new RemediationAction(UUID.randomUUID(), incidentId, "echo hello-sandbox",
                RemediationStatus.EXECUTING, null, null, OffsetDateTime.now());
        given(remediationActionRepository.update(any())).willAnswer(invocation -> invocation.getArgument(0));
        OffsetDateTime executedAt = OffsetDateTime.now();

        RemediationAction result = stateMachine.commitClosure(executing, 0, "hello-sandbox\n", executedAt);

        assertThat(result.getExecutionStatus()).isEqualTo(RemediationStatus.SUCCESS);
        assertThat(result.getExecutionLog()).isEqualTo("hello-sandbox\n");
        assertThat(result.getExecutedAt()).isEqualTo(executedAt);
        verify(incidentRepository).updateStatus(incidentId, IncidentStatus.RESOLVED);
    }

    @Test
    @DisplayName("should close with FAILED status and NOT resolve the incident when the exit code is non-zero")
    void should_close_with_failed_and_not_resolve_incident_when_exit_code_nonzero() {
        stateMachine = new RemediationStateMachine(remediationActionRepository, incidentRepository);
        RemediationAction executing = new RemediationAction(UUID.randomUUID(), incidentId, "echo hello-sandbox",
                RemediationStatus.EXECUTING, null, null, OffsetDateTime.now());
        given(remediationActionRepository.update(any())).willAnswer(invocation -> invocation.getArgument(0));

        RemediationAction result = stateMachine.commitClosure(executing, 1, "boom\n", OffsetDateTime.now());

        assertThat(result.getExecutionStatus()).isEqualTo(RemediationStatus.FAILED);
        verify(incidentRepository, never()).updateStatus(any(), any());
    }
}
