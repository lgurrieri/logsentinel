package com.logsentinel.domain.model;

/**
 * Value object describing the outcome of a single {@code SecuritySandbox}
 * execution (LOG-US4-BE-01, extended by LOG-US4-BE-02B): the process exit code,
 * its {@code stdout} and {@code stderr} buffers captured as two INDEPENDENT
 * streams, and whether the background Watchdog had to forcibly terminate it for
 * exceeding the caller-supplied timeout. Pure domain object — NO framework
 * dependency (no JPA, no Spring).
 * <p>
 * Prior to LOG-US4-BE-02B this record exposed a single {@code output} field with
 * stdout/stderr already merged (via {@code ProcessBuilder.redirectErrorStream(true)}).
 * That combined shape made it impossible for any downstream consumer (persistence,
 * API response, frontend) to tell which lines came from which stream without
 * unreliable text heuristics — {@code stdout}/{@code stderr} are now captured and
 * exposed separately instead.
 * <p>
 * This result intentionally carries no reference to any incident or audit
 * record — persisting it as a {@code remediation_actions} row is the
 * responsibility of the two-phase transactional flow (LOG-US4-BE-02).
 *
 * @param exitCode the subprocess exit code (undefined precise value when
 *                 {@code timedOut} is {@code true}, since the process was
 *                 forcibly killed rather than exiting on its own)
 * @param stdout   the standard output text captured while the subprocess ran,
 *                 read from {@code process.getInputStream()}
 * @param stderr   the standard error text captured while the subprocess ran,
 *                 read from {@code process.getErrorStream()}, independently of
 *                 {@code stdout}
 * @param timedOut {@code true} if the Watchdog destroyed the subprocess for
 *                 exceeding the timeout, {@code false} if it exited on its own
 */
public record SandboxExecutionResult(int exitCode, String stdout, String stderr, boolean timedOut) {
}
