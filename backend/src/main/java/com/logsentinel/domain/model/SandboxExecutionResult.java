package com.logsentinel.domain.model;

/**
 * Value object describing the outcome of a single {@code SecuritySandbox}
 * execution (LOG-US4-BE-01): the process exit code, its combined stdout/stderr
 * output, and whether the background Watchdog had to forcibly terminate it for
 * exceeding the caller-supplied timeout. Pure domain object — NO framework
 * dependency (no JPA, no Spring).
 * <p>
 * This result intentionally carries no reference to any incident or audit
 * record — persisting it as a {@code remediation_actions} row is the
 * responsibility of the future two-phase transactional flow (LOG-US4-BE-02).
 *
 * @param exitCode the subprocess exit code (undefined precise value when
 *                 {@code timedOut} is {@code true}, since the process was
 *                 forcibly killed rather than exiting on its own)
 * @param output   the combined stdout/stderr text captured while the
 *                  subprocess ran
 * @param timedOut {@code true} if the Watchdog destroyed the subprocess for
 *                 exceeding the timeout, {@code false} if it exited on its own
 */
public record SandboxExecutionResult(int exitCode, String output, boolean timedOut) {
}
