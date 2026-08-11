package com.logsentinel.domain.exception;

/**
 * Thrown when the {@code SecuritySandbox} refuses to run a subprocess because
 * its isolation guarantees cannot be honored (LOG-US4-BE-01): either the JVM
 * is not running under the configured restricted non-root system user, or the
 * isolated subprocess could not be started at all. Pure domain exception — no
 * framework dependency.
 */
public class SandboxSecurityException extends RuntimeException {

    public SandboxSecurityException(String message) {
        super(message);
    }

    public SandboxSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
