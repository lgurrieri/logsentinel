package com.logsentinel.infrastructure.adapters.out.sandbox;

import com.logsentinel.domain.exception.InvalidRemediationScriptException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated suite for LOG-US4-TEST-03: submits {@link CommandAllowlist} to the
 * strict matrix of 5 classic Bash injection metacharacters required by the
 * ticket's acceptance criteria ({@code |}, {@code &&}, {@code $(...)}, the
 * backtick, and {@code >}).
 *
 * <p>The matrix has two axes: each of the 5 vectors is exercised both (a)
 * appended after an otherwise-allowed single-line script, and (b) embedded on
 * a script line other than the one {@link CommandAllowlist} inspects to
 * extract the base command — proving the rejection is a whole-script scan,
 * not merely a check of the first line. Two dimensions considered and
 * deliberately NOT added as separate cases, because {@link CommandAllowlist}
 * rejects on a plain {@code String.contains(...)} per vector and neither
 * exercises a distinct code path: combining multiple vectors in one script
 * (the first match in iteration order already short-circuits validation),
 * and nested/doubled backticks (any single backtick anywhere already matches).
 *
 * <p>The base "does the policy engage at all" cases (allowlisted vs.
 * non-allowlisted base command, shebang handling, blank/null script) live in
 * {@link CommandAllowlistTest} and are intentionally not duplicated here.
 */
@DisplayName("CommandAllowlist — LOG-US4-TEST-03 Bash injection matrix")
class BashInjectionMatrixTest {

    private final CommandAllowlist allowlist = new CommandAllowlist(Set.of("echo", "ansible-playbook", "systemctl"));

    @ParameterizedTest(name = "[{index}] rejects metacharacter \"{0}\" appended after the base command")
    @ValueSource(strings = { "|", "&&", "$(", "`", ">" })
    @DisplayName("rejects a script containing any of the 5 classic Bash injection metacharacters")
    void should_reject_script_containing_forbidden_metacharacter(String forbiddenMetacharacter) {
        String maliciousScript = "echo safe " + forbiddenMetacharacter + " cat /etc/passwd";

        assertThatThrownBy(() -> allowlist.validate(maliciousScript))
                .isInstanceOf(InvalidRemediationScriptException.class);
    }

    @ParameterizedTest(name = "[{index}] rejects metacharacter \"{0}\" embedded on a line other than the base command's")
    @ValueSource(strings = { "|", "&&", "$(", "`", ">" })
    @DisplayName("rejects a script where the forbidden metacharacter appears on a later line, not the base command's own line")
    void should_reject_script_containing_forbidden_metacharacter_on_a_later_line(String forbiddenMetacharacter) {
        String maliciousScript = ("#!/bin/bash\n"
                + "echo safe\n"
                + "cat /etc/passwd " + forbiddenMetacharacter + " dump.txt");

        assertThatThrownBy(() -> allowlist.validate(maliciousScript))
                .isInstanceOf(InvalidRemediationScriptException.class);
    }
}
