package com.logsentinel.domain.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure domain component (LOG-US3-DB-02B, design decision Option B, approved
 * 2026-08-11) that deterministically derives {@code IncidentDiagnostic.suggestedScript}
 * from the fully-consolidated {@code diagnosticText} produced by the AI, so the backend
 * — not the client — is the authoritative source of the remediation script.
 * <p>
 * Extracts the content of the first Markdown fenced code block delimited by triple
 * backticks, with or without a language hint (e.g. {@code ```bash}, {@code ```yaml}, or
 * {@code ```} alone). No framework dependency — plain Java, safe to call synchronously
 * and repeatedly.
 * <p>
 * Deterministic rules, by design — never guess, never concatenate ambiguous text:
 * <ul>
 *     <li>No fenced code block present -&gt; returns {@code null}.</li>
 *     <li>An opening fence that is never closed by a matching triple backtick -&gt;
 *         returns {@code null}.</li>
 *     <li>A fenced code block whose content is blank once trimmed -&gt; returns
 *         {@code null} (an empty script is not a usable remediation action).</li>
 *     <li>Multiple fenced code blocks present -&gt; the FIRST one, left to right, is
 *         used. This is a conscious simplification: the AI is prompted to lead with the
 *         primary remediation script, so "first" is the practical, unambiguous rule
 *         rather than attempting to infer intent between candidates.</li>
 * </ul>
 */
public final class SuggestedScriptExtractor {

    /**
     * Matches ``` optionally followed by a language hint up to the end of that line,
     * then the block content (non-greedy, DOTALL so it spans newlines) up to the next
     * ```. Requiring a matching closing fence is what makes an unclosed opening fence
     * naturally produce no match, without any extra bookkeeping.
     */
    private static final Pattern FENCED_CODE_BLOCK =
            Pattern.compile("```[^\\n\\r]*\\r?\\n(.*?)```", Pattern.DOTALL);

    public String extract(String diagnosticText) {
        if (diagnosticText == null) {
            return null;
        }
        Matcher matcher = FENCED_CODE_BLOCK.matcher(diagnosticText);
        if (!matcher.find()) {
            return null;
        }
        String content = matcher.group(1).strip();
        return content.isEmpty() ? null : content;
    }
}
