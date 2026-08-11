package com.logsentinel.infrastructure.adapters.out.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SandboxWatchdog} (LOG-US4-BE-01). Spawns real, short-lived
 * OS subprocesses (no Docker, no Spring context) to prove the background control
 * thread required by the ticket actually enforces the timeout boundary by calling
 * {@code Process#destroyForcibly()} — never a real remediation script, only the
 * generic {@code sleep}/{@code echo} OS commands used purely to drive this test.
 */
class SandboxWatchdogTest {

    @Test
    @DisplayName("should forcibly destroy the process and report timedOut when it exceeds the timeout")
    void should_destroy_process_when_it_exceeds_timeout() throws IOException {
        Process process = new ProcessBuilder("sleep", "5").start();

        SandboxWatchdog watchdog = new SandboxWatchdog(process, 200, TimeUnit.MILLISECONDS);
        watchdog.start();
        watchdog.awaitCompletion();

        assertThat(watchdog.timedOut()).isTrue();
        assertThat(process.isAlive()).isFalse();
    }

    @Test
    @DisplayName("should not report timedOut when the process finishes before the timeout")
    void should_not_report_timed_out_when_process_finishes_in_time() throws IOException {
        Process process = new ProcessBuilder("echo", "quick").start();

        SandboxWatchdog watchdog = new SandboxWatchdog(process, 5, TimeUnit.SECONDS);
        watchdog.start();
        watchdog.awaitCompletion();

        assertThat(watchdog.timedOut()).isFalse();
        assertThat(process.isAlive()).isFalse();
    }
}
