package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.application.ports.in.ExecuteRemediationUseCase;
import com.logsentinel.application.ports.in.ExecuteRemediationUseCase.ExecuteRemediationCommand;
import com.logsentinel.domain.exception.RemediationScriptUnavailableException;
import com.logsentinel.domain.model.RemediationAction;
import com.logsentinel.domain.model.RemediationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit test for {@link RemediationController} using standalone MockMvc setup
 * (LOG-US4-BE-02). No request body is ever sent — {@code generatedScript} is
 * resolved server-side from the incident's persisted diagnostic
 * (LOG-US3-DB-02B, Option B), so only the path variable and the mocked use case
 * outcome are exercised here.
 */
@ExtendWith(MockitoExtension.class)
class RemediationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ExecuteRemediationUseCase executeRemediationUseCase;

    private final UUID incidentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RemediationController controller = new RemediationController(executeRemediationUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("should return 200 with the closed remediation action when execution succeeds")
    void should_return_200_when_execution_succeeds() throws Exception {
        UUID actionId = UUID.randomUUID();
        OffsetDateTime executedAt = OffsetDateTime.now();
        RemediationAction closed = new RemediationAction(actionId, incidentId, "systemctl restart payment-gw",
                RemediationStatus.SUCCESS, "restarted\n", "", executedAt, executedAt);
        given(executeRemediationUseCase.execute(eq(new ExecuteRemediationCommand(incidentId)))).willReturn(closed);

        mockMvc.perform(post("/api/v1/incidents/{id}/remediations", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(actionId.toString()))
                .andExpect(jsonPath("$.generatedScript").value("systemctl restart payment-gw"))
                .andExpect(jsonPath("$.executionStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.stdoutLog").value("restarted\n"))
                .andExpect(jsonPath("$.stderrLog").value(""))
                .andExpect(jsonPath("$.executedAt").exists());
    }

    @Test
    @DisplayName("should return 409 without creating an audit record when no remediation script is available")
    void should_return_409_when_script_unavailable() throws Exception {
        given(executeRemediationUseCase.execute(eq(new ExecuteRemediationCommand(incidentId))))
                .willThrow(new RemediationScriptUnavailableException(incidentId));

        mockMvc.perform(post("/api/v1/incidents/{id}/remediations", incidentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("should not expose stacktrace in error response")
    void should_not_expose_stacktrace_in_error_response() throws Exception {
        given(executeRemediationUseCase.execute(eq(new ExecuteRemediationCommand(incidentId))))
                .willThrow(new RuntimeException("sandbox process crashed unexpectedly"));

        mockMvc.perform(post("/api/v1/incidents/{id}/remediations", incidentId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
