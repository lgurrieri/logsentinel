package com.logsentinel.infrastructure.adapters.out.sandbox;

import com.logsentinel.domain.exception.InvalidRemediationScriptException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link CommandAllowlist} (LOG-US4-BE-01). Pure Java — no Spring
 * context. Proves the sanitization component rejects both non-approved base
 * commands and the classic Bash injection metacharacters, which is the "solid
 * foundation" the dedicated 5-vector matrix suite of LOG-US4-TEST-03 exercises
 * exhaustively — this class only covers the base cases required to prove the
 * policy actually works, not the full matrix.
 */
class CommandAllowlistTest {

    private final CommandAllowlist allowlist = new CommandAllowlist(Set.of("echo", "ansible-playbook", "systemctl"));

    @Test
    @DisplayName("should accept a script whose base command is in the allowlist")
    void should_accept_script_when_base_command_is_allowed() {
        assertThatNoException().isThrownBy(() -> allowlist.validate("echo hello-world"));
    }

    @Test
    @DisplayName("should accept a script prefixed with a shebang line, ignoring it when extracting the base command")
    void should_ignore_shebang_line_when_extracting_base_command() {
        assertThatNoException().isThrownBy(() ->
                allowlist.validate("#!/bin/bash\nansible-playbook -i production kill-idle-conns.yml"));
    }

    @Test
    @DisplayName("should reject a script whose base command is not in the allowlist")
    void should_reject_script_when_base_command_is_not_allowed() {
        assertThatThrownBy(() -> allowlist.validate("rm -rf /tmp/should-not-run"))
                .isInstanceOf(InvalidRemediationScriptException.class);
    }

    @Test
    @DisplayName("should reject a blank script")
    void should_reject_blank_script() {
        assertThatThrownBy(() -> allowlist.validate("   "))
                .isInstanceOf(InvalidRemediationScriptException.class);
    }

    @Test
    @DisplayName("should reject a null script")
    void should_reject_null_script() {
        assertThatThrownBy(() -> allowlist.validate(null))
                .isInstanceOf(InvalidRemediationScriptException.class);
    }

    @ParameterizedTest(name = "should reject script containing forbidden metacharacter [{0}]")
    @ValueSource(strings = { "|", "&&", "$(", "`", ">" })
    @DisplayName("should reject a script containing any of the 5 classic Bash injection metacharacters")
    void should_reject_script_containing_forbidden_metacharacter(String forbiddenMetacharacter) {
        String maliciousScript = "echo safe " + forbiddenMetacharacter + " cat /etc/passwd";

        assertThatThrownBy(() -> allowlist.validate(maliciousScript))
                .isInstanceOf(InvalidRemediationScriptException.class);
    }
}
