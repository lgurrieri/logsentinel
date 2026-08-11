package com.logsentinel.application.ports.out;

import java.util.function.Consumer;

/**
 * Driven port (SPI) abstracting the streaming call to the active LLM chat provider
 * (Ollama by default, OpenAI under the {@code openai} profile — selected via
 * {@code spring.ai.model.chat}, LOG-CORE-INFRA-01). Pure Java interface — NO import of
 * Spring AI's {@code ChatClient} here, so the application layer never depends on a
 * specific AI framework type (LOG-US3-BE-01).
 */
public interface DiagnosticChatPort {

    /**
     * Streams the LLM diagnosis for the given prompts, invoking {@code onChunk} once
     * per text fragment as it arrives, in the order received. This call blocks the
     * calling thread until the underlying stream completes or fails — callers running
     * this on the request thread MUST offload it (e.g. {@code @Async}), never call it
     * synchronously from an HTTP request-handling thread.
     *
     * @param systemPrompt the system prompt (SRE persona + runbook context augmentation)
     * @param userPrompt   the user prompt (the incident to diagnose)
     * @param onChunk      callback invoked once per text fragment, in arrival order
     */
    void streamDiagnosis(String systemPrompt, String userPrompt, Consumer<String> onChunk);
}
