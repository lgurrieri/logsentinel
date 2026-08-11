package com.logsentinel.infrastructure.adapters.out.sandbox;

import com.logsentinel.domain.exception.InvalidRemediationScriptException;
import com.logsentinel.domain.exception.SandboxSecurityException;
import com.logsentinel.domain.model.SandboxExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ProcessBuilderSecuritySandbox} (LOG-US4-BE-01) — the core
 * {@link com.logsentinel.application.ports.out.SecuritySandbox} implementation.
 * No Docker, no Spring context: only real, short-lived, generic OS subprocesses
 * (echo/ls/sleep) spawned directly by this test to drive the sandbox in isolation.
 * <p>
 * IMPORTANT: nothing here executes a real remediation script — there is no
 * business flow wired to this component yet (that is LOG-US4-BE-02). Every
 * command below is a harmless, generic OS command used solely to prove the
 * sandbox's own isolation mechanics (Allowlist, Watchdog, restricted user guard).
 */
class ProcessBuilderSecuritySandboxTest {

    private static final String CURRENT_JVM_USER = System.getProperty("user.name");

    @Test
    @DisplayName("should execute an allowed command and capture its combined output with exit code 0")
    void should_execute_allowed_command_and_capture_output() {
        var sandbox = new ProcessBuilderSecuritySandbox("echo", CURRENT_JVM_USER, "bash");

        SandboxExecutionResult result = sandbox.executeInIsolation("echo hello-sandbox", 5, TimeUnit.SECONDS);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("hello-sandbox");
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    @DisplayName("should capture stderr merged with stdout and report a non-zero exit code when the command fails")
    void should_capture_stderr_and_nonzero_exit_code_on_failure() {
        var sandbox = new ProcessBuilderSecuritySandbox("ls", CURRENT_JVM_USER, "bash");

        SandboxExecutionResult result = sandbox.executeInIsolation(
                "ls /this-path-should-not-exist-on-any-machine", 5, TimeUnit.SECONDS);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).isNotBlank();
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    @DisplayName("should mark timedOut and forcibly destroy the subprocess when it exceeds the timeout")
    void should_mark_timed_out_when_execution_exceeds_timeout() {
        var sandbox = new ProcessBuilderSecuritySandbox("sleep", CURRENT_JVM_USER, "bash");

        SandboxExecutionResult result = sandbox.executeInIsolation("sleep 5", 200, TimeUnit.MILLISECONDS);

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isNotZero();
    }

    @Test
    @DisplayName("should reject a script whose base command is not in the allowlist, without spawning a process")
    void should_reject_script_not_in_allowlist() {
        var sandbox = new ProcessBuilderSecuritySandbox("echo", CURRENT_JVM_USER, "bash");

        assertThatThrownBy(() -> sandbox.executeInIsolation("rm -rf /tmp/anything", 5, TimeUnit.SECONDS))
                .isInstanceOf(InvalidRemediationScriptException.class);
    }

    @ParameterizedTest(name = "should reject script containing forbidden metacharacter [{0}]")
    @ValueSource(strings = { "|", "&&", "$(", "`", ">" })
    @DisplayName("should reject a script containing a classic Bash injection metacharacter even with an allowed base command")
    void should_reject_script_with_forbidden_metacharacter(String forbiddenMetacharacter) {
        var sandbox = new ProcessBuilderSecuritySandbox("echo", CURRENT_JVM_USER, "bash");
        String maliciousScript = "echo safe " + forbiddenMetacharacter + " cat /etc/passwd";

        assertThatThrownBy(() -> sandbox.executeInIsolation(maliciousScript, 5, TimeUnit.SECONDS))
                .isInstanceOf(InvalidRemediationScriptException.class);
    }

    @Test
    @DisplayName("should refuse to execute when the JVM is not running as the configured restricted user")
    void should_refuse_execution_when_not_running_as_restricted_user() {
        var sandbox = new ProcessBuilderSecuritySandbox("echo", "definitely-not-" + CURRENT_JVM_USER, "bash");

        assertThatThrownBy(() -> sandbox.executeInIsolation("echo hello", 5, TimeUnit.SECONDS))
                .isInstanceOf(SandboxSecurityException.class);
    }

    @Test
    @DisplayName("should refuse construction when the sandbox is configured to run as root")
    void should_refuse_construction_when_restricted_user_is_root() {
        assertThatThrownBy(() -> new ProcessBuilderSecuritySandbox("echo", "root", "bash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should wrap a subprocess start failure in a SandboxSecurityException")
    void should_wrap_subprocess_start_failure() {
        var sandbox = new ProcessBuilderSecuritySandbox("echo", CURRENT_JVM_USER, "definitely-not-a-real-shell-xyz");

        assertThatThrownBy(() -> sandbox.executeInIsolation("echo hello", 5, TimeUnit.SECONDS))
                .isInstanceOf(SandboxSecurityException.class);
    }
}
