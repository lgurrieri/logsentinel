package com.logsentinel.application.service;

import com.logsentinel.application.ports.in.ExecuteRemediationUseCase.ExecuteRemediationCommand;
import com.logsentinel.application.ports.out.IncidentDiagnosticRepository;
import com.logsentinel.application.ports.out.SecuritySandbox;
import com.logsentinel.domain.exception.InvalidRemediationScriptException;
import com.logsentinel.domain.exception.RemediationScriptUnavailableException;
import com.logsentinel.domain.model.IncidentDiagnostic;
import com.logsentinel.domain.model.RemediationAction;
import com.logsentinel.domain.model.RemediationStatus;
import com.logsentinel.domain.model.SandboxExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link ExecuteRemediationService} (LOG-US4-BE-02) — the
 * orchestrator of the full remediation flow: resolve {@code generatedScript} from
 * the persisted {@code IncidentDiagnostic.suggestedScript} (LOG-US3-DB-02B, Option B)
 * → Transaction A (commit EXECUTING) → untransactional sandbox execution →
 * Transaction B (commit closure). All collaborators are mocked here — no Spring
 * context, no real subprocess, no real database.
 */
@ExtendWith(MockitoExtension.class)
class ExecuteRemediationServiceTest {

    @Mock
    private RemediationStateMachine stateMachine;

    @Mock
    private SecuritySandbox securitySandbox;

    @Mock
    private IncidentDiagnosticRepository incidentDiagnosticRepository;

    private ExecuteRemediationService service;

    private final UUID incidentId = UUID.randomUUID();

    @Test
    @DisplayName("should resolve the script from the persisted diagnostic, commit EXECUTING before invoking the sandbox, then commit closure with the sandbox result")
    void should_commit_executing_before_sandbox_then_commit_closure_with_result() {
        service = new ExecuteRemediationService(stateMachine, securitySandbox, incidentDiagnosticRepository, 30L);
        IncidentDiagnostic diagnostic = IncidentDiagnostic.createNew(incidentId, "diagnostic text", "echo hello-sandbox");
        given(incidentDiagnosticRepository.findByIncidentId(incidentId)).willReturn(Optional.of(diagnostic));
        RemediationAction executing = new RemediationAction(UUID.randomUUID(), incidentId, "echo hello-sandbox",
                RemediationStatus.EXECUTING, null, null, null, OffsetDateTime.now());
        given(stateMachine.commitExecuting(incidentId, "echo hello-sandbox")).willReturn(executing);
        given(securitySandbox.executeInIsolation(eq("echo hello-sandbox"), anyLong(), any(TimeUnit.class)))
                .willReturn(new SandboxExecutionResult(0, "hello-sandbox\n", "", false));
        RemediationAction closed = executing.closeWith(RemediationStatus.SUCCESS, "hello-sandbox\n", "", OffsetDateTime.now());
        given(stateMachine.commitClosure(eq(executing), eq(0), eq("hello-sandbox\n"), eq(""), any())).willReturn(closed);

        RemediationAction result = service.execute(new ExecuteRemediationCommand(incidentId));

        assertThat(result.getExecutionStatus()).isEqualTo(RemediationStatus.SUCCESS);

        InOrder inOrder = inOrder(incidentDiagnosticRepository, stateMachine, securitySandbox);
        inOrder.verify(incidentDiagnosticRepository).findByIncidentId(incidentId);
        inOrder.verify(stateMachine).commitExecuting(incidentId, "echo hello-sandbox");
        inOrder.verify(securitySandbox).executeInIsolation(eq("echo hello-sandbox"), anyLong(), any(TimeUnit.class));
        inOrder.verify(stateMachine).commitClosure(eq(executing), eq(0), eq("hello-sandbox\n"), eq(""), any());
    }

    @Test
    @DisplayName("should pass a non-zero exit code through to commitClosure unchanged")
    void should_pass_nonzero_exit_code_to_closure() {
        service = new ExecuteRemediationService(stateMachine, securitySandbox, incidentDiagnosticRepository, 30L);
        IncidentDiagnostic diagnostic = IncidentDiagnostic.createNew(incidentId, "diagnostic text", "echo boom");
        given(incidentDiagnosticRepository.findByIncidentId(incidentId)).willReturn(Optional.of(diagnostic));
        RemediationAction executing = new RemediationAction(UUID.randomUUID(), incidentId, "echo boom",
                RemediationStatus.EXECUTING, null, null, null, OffsetDateTime.now());
        given(stateMachine.commitExecuting(incidentId, "echo boom")).willReturn(executing);
        given(securitySandbox.executeInIsolation(eq("echo boom"), anyLong(), any(TimeUnit.class)))
                .willReturn(new SandboxExecutionResult(1, "", "boom\n", false));
        given(stateMachine.commitClosure(any(), anyInt(), anyString(), anyString(), any()))
                .willReturn(executing.closeWith(RemediationStatus.FAILED, "", "boom\n", OffsetDateTime.now()));

        service.execute(new ExecuteRemediationCommand(incidentId));

        verify(stateMachine).commitClosure(eq(executing), eq(1), eq(""), eq("boom\n"), any());
    }

    @Test
    @DisplayName("should still commit a closure when the sandbox rejects the script instead of leaving the audit record stuck in EXECUTING")
    void should_commit_closure_when_sandbox_rejects_script() {
        service = new ExecuteRemediationService(stateMachine, securitySandbox, incidentDiagnosticRepository, 30L);
        IncidentDiagnostic diagnostic = IncidentDiagnostic.createNew(incidentId, "diagnostic text", "rm -rf /tmp");
        given(incidentDiagnosticRepository.findByIncidentId(incidentId)).willReturn(Optional.of(diagnostic));
        RemediationAction executing = new RemediationAction(UUID.randomUUID(), incidentId, "rm -rf /tmp",
                RemediationStatus.EXECUTING, null, null, null, OffsetDateTime.now());
        given(stateMachine.commitExecuting(incidentId, "rm -rf /tmp")).willReturn(executing);
        given(securitySandbox.executeInIsolation(eq("rm -rf /tmp"), anyLong(), any(TimeUnit.class)))
                .willThrow(new InvalidRemediationScriptException("command not in allowlist"));
        given(stateMachine.commitClosure(any(), anyInt(), any(), anyString(), any()))
                .willReturn(executing.closeWith(RemediationStatus.FAILED, null, "rejected", OffsetDateTime.now()));

        RemediationAction result = service.execute(new ExecuteRemediationCommand(incidentId));

        assertThat(result.getExecutionStatus()).isEqualTo(RemediationStatus.FAILED);
        ArgumentCaptor<Integer> exitCodeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> stdoutCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> stderrCaptor = ArgumentCaptor.forClass(String.class);
        verify(stateMachine).commitClosure(eq(executing), exitCodeCaptor.capture(), stdoutCaptor.capture(),
                stderrCaptor.capture(), any());
        assertThat(exitCodeCaptor.getValue()).isNotZero();
        assertThat(stdoutCaptor.getValue()).isNull();
        assertThat(stderrCaptor.getValue()).contains("command not in allowlist");
    }

    @Test
    @DisplayName("should throw RemediationScriptUnavailableException and never touch the state machine or sandbox when no diagnostic is persisted for the incident")
    void should_throw_when_no_diagnostic_persisted() {
        service = new ExecuteRemediationService(stateMachine, securitySandbox, incidentDiagnosticRepository, 30L);
        given(incidentDiagnosticRepository.findByIncidentId(incidentId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new ExecuteRemediationCommand(incidentId)))
                .isInstanceOf(RemediationScriptUnavailableException.class);

        verifyNoInteractions(stateMachine, securitySandbox);
    }

    @Test
    @DisplayName("should throw RemediationScriptUnavailableException and never touch the state machine or sandbox when suggestedScript is null")
    void should_throw_when_suggested_script_is_null() {
        service = new ExecuteRemediationService(stateMachine, securitySandbox, incidentDiagnosticRepository, 30L);
        IncidentDiagnostic diagnosticWithoutScript = IncidentDiagnostic.createNew(incidentId, "diagnostic text with no fenced code block", null);
        given(incidentDiagnosticRepository.findByIncidentId(incidentId)).willReturn(Optional.of(diagnosticWithoutScript));

        assertThatThrownBy(() -> service.execute(new ExecuteRemediationCommand(incidentId)))
                .isInstanceOf(RemediationScriptUnavailableException.class);

        verify(stateMachine, never()).commitExecuting(any(), anyString());
        verifyNoInteractions(securitySandbox);
    }
}
