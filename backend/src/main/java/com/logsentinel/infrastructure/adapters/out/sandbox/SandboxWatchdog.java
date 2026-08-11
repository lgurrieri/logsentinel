package com.logsentinel.infrastructure.adapters.out.sandbox;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background control thread that enforces the timeout boundary of a sandboxed
 * subprocess, as mandated by LOG-US4-BE-01. Runs concurrently with the caller
 * reading the subprocess output; if the wrapped {@link Process} does not
 * terminate within the given timeout, this Watchdog forcibly destroys it
 * ({@link Process#destroyForcibly()}) so a runaway script can never block the
 * sandbox indefinitely.
 */
public class SandboxWatchdog {

    private final Process process;
    private final long timeout;
    private final TimeUnit unit;
    private final AtomicBoolean timedOut = new AtomicBoolean(false);
    private final Thread watchdogThread;

    public SandboxWatchdog(Process process, long timeout, TimeUnit unit) {
        this.process = process;
        this.timeout = timeout;
        this.unit = unit;
        this.watchdogThread = new Thread(this::watch, "sandbox-watchdog");
        this.watchdogThread.setDaemon(true);
    }

    /** Starts monitoring the wrapped process in the background. */
    public void start() {
        watchdogThread.start();
    }

    /**
     * Blocks until the Watchdog has finished monitoring — i.e. until the
     * process has terminated, either on its own or forcibly destroyed after
     * exceeding the timeout. Safe to call {@link Process#exitValue()}
     * immediately after this method returns.
     */
    public void awaitCompletion() {
        try {
            watchdogThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** @return {@code true} if the process had to be forcibly destroyed for exceeding the timeout */
    public boolean timedOut() {
        return timedOut.get();
    }

    private void watch() {
        try {
            boolean finishedInTime = process.waitFor(timeout, unit);
            if (!finishedInTime) {
                timedOut.set(true);
                process.destroyForcibly();
                process.waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
