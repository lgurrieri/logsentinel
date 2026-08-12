package com.logsentinel.infrastructure.adapters.out.sandbox;

import com.logsentinel.domain.exception.InvalidRemediationScriptException;

import java.util.List;
import java.util.Set;

/**
 * Enforces the Allowlist isolation policy required by LOG-US4-BE-01: a script
 * is only allowed to run in the {@link ProcessBuilderSecuritySandbox} if
 * <ol>
 *     <li>it does not contain any of the 5 classic Bash injection
 *         metacharacters ({@code |}, {@code &&}, {@code $(}, the backtick, or
 *         {@code >}), and</li>
 *     <li>its base command (the first non-blank, non-comment/shebang line's
 *         first token) is present in the configured allowlist.</li>
 * </ol>
 * This is the sanitization component the dedicated 5-vector injection matrix
 * of LOG-US4-TEST-03 exercises exhaustively — this class only covers the base
 * cases required to prove the policy actually works, not the full matrix.
 */
public class CommandAllowlist {

    /** The 5 classic Bash injection vectors this Allowlist rejects outright. */
    private static final List<String> FORBIDDEN_PATTERNS = List.of("|", "&&", "$(", "`", ">");

    private final Set<String> allowedCommands;

    public CommandAllowlist(Set<String> allowedCommands) {
        this.allowedCommands = Set.copyOf(allowedCommands);
    }

    /**
     * Validates {@code script} against the isolation policy.
     *
     * @throws InvalidRemediationScriptException if the script is blank, contains a
     *                                            forbidden metacharacter, or its base
     *                                            command is not allowlisted
     */
    public void validate(String script) {
        if (script == null || script.isBlank()) {
            throw new InvalidRemediationScriptException("Script must not be blank");
        }
        for (String forbidden : FORBIDDEN_PATTERNS) {
            if (script.contains(forbidden)) {
                throw new InvalidRemediationScriptException(
                        "Script contains forbidden shell metacharacter: " + forbidden);
            }
        }
        String baseCommand = extractBaseCommand(script);
        if (!allowedCommands.contains(baseCommand)) {
            throw new InvalidRemediationScriptException(
                    "Command not present in sandbox allowlist: " + baseCommand);
        }
    }

    private String extractBaseCommand(String script) {
        return script.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .findFirst()
                .map(line -> line.split("\\s+")[0])
                .orElseThrow(() -> new InvalidRemediationScriptException("No executable command found in script"));
    }
}
