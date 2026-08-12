package com.logsentinel.infrastructure.adapters.out.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Drains a subprocess pipe ({@code stdout} or {@code stderr}) on a dedicated
 * background thread (LOG-US4-BE-02B). Required as soon as
 * {@link ProcessBuilderSecuritySandbox} stopped merging both streams via
 * {@code ProcessBuilder.redirectErrorStream(true)}: with two independent pipes,
 * reading one of them synchronously to EOF before starting the other risks a
 * deadlock if the subprocess blocks writing to the still-undrained pipe once
 * its OS buffer fills up. Draining both pipes concurrently, each on its own
 * {@code StreamGobbler}, avoids that regardless of how much a script writes to
 * either channel.
 */
class StreamGobbler {

    private static final Logger log = LoggerFactory.getLogger(StreamGobbler.class);

    private final InputStream inputStream;
    private final Thread thread;
    private volatile String result = "";

    StreamGobbler(InputStream inputStream) {
        this.inputStream = inputStream;
        this.thread = new Thread(this::drain, "sandbox-stream-gobbler");
        this.thread.setDaemon(true);
    }

    /** Starts draining the wrapped stream in the background. */
    void start() {
        thread.start();
    }

    /**
     * Blocks until the stream has reached EOF (i.e. the subprocess closed it,
     * typically because it terminated), then returns everything read.
     */
    String awaitResult() {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }

    private void drain() {
        StringBuilder buffer = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            log.error("Sandbox failed to read subprocess stream", Map.of("cause", String.valueOf(e.getMessage())));
        }
        result = buffer.toString();
    }
}
