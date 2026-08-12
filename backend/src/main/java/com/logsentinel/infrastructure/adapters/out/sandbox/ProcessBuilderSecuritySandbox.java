package com.logsentinel.infrastructure.adapters.out.sandbox;

import com.logsentinel.application.ports.out.SecuritySandbox;
import com.logsentinel.domain.exception.SandboxSecurityException;
import com.logsentinel.domain.model.SandboxExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * {@link SecuritySandbox} implementation required by LOG-US4-BE-01: invokes OS
 * subprocesses via {@link ProcessBuilder} under strict isolation —
 * <ol>
 *     <li><b>Allowlist:</b> {@link CommandAllowlist} rejects any script whose
 *         base command is not explicitly permitted, or that contains a
 *         classic Bash injection metacharacter.</li>
 *     <li><b>Restricted non-root execution:</b> the sandbox refuses to run
 *         anything unless the JVM itself is running as the configured
 *         restricted system user (never {@code root}) — defense in depth on
 *         top of the container already running as non-root
 *         ({@code backend/Dockerfile}, {@code USER appuser}).</li>
 *     <li><b>Watchdog:</b> a background {@link SandboxWatchdog} control
 *         thread forcibly destroys the subprocess ({@code destroyForcibly()})
 *         if it exceeds the caller-supplied timeout.</li>
 * </ol>
 * Since LOG-US4-BE-02B, {@code stdout} ({@code process.getInputStream()}) and
 * {@code stderr} ({@code process.getErrorStream()}) are captured into two
 * independent buffers via {@link StreamGobbler}, each drained on its own
 * background thread — {@code redirectErrorStream(true)} is deliberately NOT
 * used anymore, so downstream consumers (persistence, API, frontend) can tell
 * the two channels apart without unreliable text heuristics.
 * <p>
 * Nothing in this codebase currently wires this component to a real
 * remediation flow — persisting an execution as an audit record is the
 * responsibility of the future two-phase transactional flow (LOG-US4-BE-02).
 */
@Component
public class ProcessBuilderSecuritySandbox implements SecuritySandbox {

    private static final Logger log = LoggerFactory.getLogger(ProcessBuilderSecuritySandbox.class);

    private final CommandAllowlist allowlist;
    private final String restrictedExecutionUser;
    private final String shellExecutable;

    public ProcessBuilderSecuritySandbox(
            @Value("${logsentinel.sandbox.allowlist:echo,ansible-playbook,systemctl}") String allowedCommandsCsv,
            @Value("${logsentinel.sandbox.executor-user:appuser}") String restrictedExecutionUser,
            @Value("${logsentinel.sandbox.shell:bash}") String shellExecutable) {
        if (restrictedExecutionUser == null || restrictedExecutionUser.isBlank()) {
            throw new IllegalArgumentException("restrictedExecutionUser must not be blank");
        }
        if ("root".equalsIgnoreCase(restrictedExecutionUser.strip())) {
            throw new IllegalArgumentException("Sandbox must not be configured to execute as 'root'");
        }
        this.allowlist = new CommandAllowlist(parseAllowlistCsv(allowedCommandsCsv));
        this.restrictedExecutionUser = restrictedExecutionUser.strip();
        this.shellExecutable = shellExecutable;
    }

    private static Set<String> parseAllowlistCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(command -> !command.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public SandboxExecutionResult executeInIsolation(String script, long timeout, TimeUnit unit) {
        assertRunningAsRestrictedUser();
        allowlist.validate(script);

        Process process = start(script);

        SandboxWatchdog watchdog = new SandboxWatchdog(process, timeout, unit);
        watchdog.start();

        StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream());
        StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream());
        stdoutGobbler.start();
        stderrGobbler.start();

        watchdog.awaitCompletion();
        String stdout = stdoutGobbler.awaitResult();
        String stderr = stderrGobbler.awaitResult();

        int exitCode = process.exitValue();
        boolean timedOut = watchdog.timedOut();

        log.info("Sandbox execution finished", Map.of(
                "exitCode", exitCode,
                "timedOut", timedOut
        ));

        return new SandboxExecutionResult(exitCode, stdout, stderr, timedOut);
    }

    private void assertRunningAsRestrictedUser() {
        String currentUser = System.getProperty("user.name");
        if (currentUser == null || "root".equalsIgnoreCase(currentUser) || !restrictedExecutionUser.equals(currentUser)) {
            log.error("Sandbox refused execution: JVM not running as restricted user", Map.of(
                    "expectedUser", restrictedExecutionUser,
                    "actualUser", String.valueOf(currentUser)
            ));
            throw new SandboxSecurityException(
                    "Sandbox refuses to execute: JVM must run as restricted non-root user '"
                            + restrictedExecutionUser + "', but is running as '" + currentUser + "'");
        }
    }

    private Process start(String script) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(shellExecutable, "-c", script);
            // Deliberately NOT calling redirectErrorStream(true) (LOG-US4-BE-02B):
            // stdout and stderr are captured into independent buffers below.
            return processBuilder.start();
        } catch (IOException e) {
            log.error("Sandbox failed to start isolated subprocess", Map.of("cause", String.valueOf(e.getMessage())));
            throw new SandboxSecurityException("Sandbox could not start the isolated subprocess", e);
        }
    }
}
