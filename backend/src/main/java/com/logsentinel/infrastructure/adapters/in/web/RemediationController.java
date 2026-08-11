package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.application.ports.in.ExecuteRemediationUseCase;
import com.logsentinel.application.ports.in.ExecuteRemediationUseCase.ExecuteRemediationCommand;
import com.logsentinel.domain.model.RemediationAction;
import com.logsentinel.infrastructure.adapters.in.web.dto.RemediationActionResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for triggering the sandboxed execution of an AI-suggested
 * remediation script (LOG-US4-BE-02).
 * <p>
 * Deliberately has NO request body (LOG-US3-DB-02B, design decision Option B,
 * approved 2026-08-11): the client never supplies executable code. The script to
 * run is resolved server-side, inside {@link ExecuteRemediationUseCase}, from the
 * {@code suggestedScript} of the incident's persisted diagnostic. If none is
 * available, the use case throws
 * {@code com.logsentinel.domain.exception.RemediationScriptUnavailableException},
 * translated by {@link GlobalExceptionHandler} into HTTP 409 Conflict.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class RemediationController {

    private final ExecuteRemediationUseCase executeRemediationUseCase;

    public RemediationController(ExecuteRemediationUseCase executeRemediationUseCase) {
        this.executeRemediationUseCase = executeRemediationUseCase;
    }

    @PostMapping("/{id}/remediations")
    public RemediationActionResponse execute(@PathVariable UUID id) {
        RemediationAction result = executeRemediationUseCase.execute(new ExecuteRemediationCommand(id));
        return toResponse(result);
    }

    private RemediationActionResponse toResponse(RemediationAction action) {
        return new RemediationActionResponse(
                action.getId(),
                action.getGeneratedScript(),
                action.getExecutionStatus(),
                action.getExecutedAt(),
                action.getExecutionLog()
        );
    }
}
