package com.logsentinel.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link SuggestedScriptExtractor} (LOG-US3-DB-02B). Pure Java — no
 * Spring context, no mocks: this is a stateless domain component that deterministically
 * derives {@code IncidentDiagnostic.suggestedScript} from the consolidated diagnostic
 * text produced by the AI, exactly once, over the fully-consolidated text (never
 * fragment by fragment — see {@code StreamDiagnosticServiceTest} for that contract).
 */
class SuggestedScriptExtractorTest {

    private final SuggestedScriptExtractor extractor = new SuggestedScriptExtractor();

    @Test
    @DisplayName("should extract the content of a fenced code block that carries a language hint (e.g. ```bash)")
    void should_extract_script_from_code_block_with_language_hint() {
        String diagnosticText = """
                Root cause: connection pool exhaustion.

                Remediation:
                ```bash
                systemctl restart payment-gw
                ```

                Monitor the service after restart.
                """;

        String result = extractor.extract(diagnosticText);

        assertThat(result).isEqualTo("systemctl restart payment-gw");
    }

    @Test
    @DisplayName("should extract the content of a fenced code block with no language hint at all")
    void should_extract_script_from_code_block_without_language_hint() {
        String diagnosticText = """
                Root cause: connection pool exhaustion.

                ```
                systemctl restart payment-gw
                ```
                """;

        String result = extractor.extract(diagnosticText);

        assertThat(result).isEqualTo("systemctl restart payment-gw");
    }

    @Test
    @DisplayName("should use the FIRST fenced code block, deterministically, when multiple blocks are present")
    void should_use_first_code_block_when_multiple_blocks_present() {
        String diagnosticText = """
                Root cause: connection pool exhaustion.

                Remediation:
                ```bash
                systemctl restart payment-gw
                ```

                Alternative (do NOT use unless the first fails):
                ```bash
                systemctl restart payment-gw --force
                ```
                """;

        String result = extractor.extract(diagnosticText);

        assertThat(result).isEqualTo("systemctl restart payment-gw");
    }

    @Test
    @DisplayName("should return null when the diagnostic text contains no fenced code block at all")
    void should_return_null_when_no_code_block_present() {
        String diagnosticText = "Root cause: connection pool exhaustion. No remediation script was generated.";

        String result = extractor.extract(diagnosticText);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should return null, never guess or concatenate, when the opening fence is never closed")
    void should_return_null_when_code_block_is_not_closed() {
        String diagnosticText = """
                Root cause: connection pool exhaustion.

                Remediation:
                ```bash
                systemctl restart payment-gw
                """;

        String result = extractor.extract(diagnosticText);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should return null when the diagnostic text is null")
    void should_return_null_when_diagnostic_text_is_null() {
        String result = extractor.extract(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should return null when the fenced code block is present but empty")
    void should_return_null_when_code_block_is_empty() {
        String diagnosticText = """
                Root cause: connection pool exhaustion.

                ```bash
                ```
                """;

        String result = extractor.extract(diagnosticText);

        assertThat(result).isNull();
    }
}
