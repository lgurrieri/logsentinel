package com.logsentinel.application.ports.out;

import com.logsentinel.domain.model.SandboxExecutionResult;

import java.util.concurrent.TimeUnit;

/**
 * Driven port (SPI) for executing an arbitrary remediation script under strict
 * isolation (LOG-US4-BE-01): a command Allowlist policy, a restricted
 * non-root execution identity, and a background Watchdog control thread that
 * forcibly terminates the subprocess if it exceeds the caller-supplied
 * timeout. Pure Java interface — NO import of Spring or JPA here.
 * <p>
 * Since LOG-US4-BE-02B, {@code stdout} and {@code stderr} are captured into two
 * independent buffers on the returned {@link SandboxExecutionResult} (no
 * {@code redirectErrorStream(true)}), so callers never need to parse a single
 * combined stream to tell which lines came from which channel.
 * <p>
 * This port intentionally has no knowledge of {@code remediation_actions} or
 * any business transaction — persisting the audit trail of an execution is
 * the responsibility of the future two-phase transactional flow
 * (LOG-US4-BE-02). Nothing in this codebase currently invokes this port from
 * a real business flow; it is the isolated execution engine itself.
 */
public interface SecuritySandbox {

    /**
     * Executes {@code script} in isolation and blocks until it finishes or the
     * timeout is reached, whichever happens first.
     *
     * @param script  the full script content to execute (e.g. a Bash script,
     *                optionally prefixed with a shebang line); validated
     *                against the configured command Allowlist before any
     *                subprocess is spawned
     * @param timeout the maximum time to let the subprocess run
     * @param unit    the unit of {@code timeout}
     * @return the independently captured stdout and stderr buffers, exit code,
     *         and whether the Watchdog had to forcibly terminate the subprocess
     */
    SandboxExecutionResult executeInIsolation(String script, long timeout, TimeUnit unit);
}
