package com.logsentinel.domain.exception;

/**
 * Thrown when a script submitted for sandboxed execution violates the
 * isolation policy enforced by the Allowlist (LOG-US4-BE-01): either its base
 * command is not present in the configured allowlist, or it contains a
 * forbidden Bash shell metacharacter that could chain additional, unapproved
 * commands (command injection). Pure domain exception — no framework
 * dependency.
 */
public class InvalidRemediationScriptException extends RuntimeException {

    public InvalidRemediationScriptException(String message) {
        super(message);
    }
}
