package com.logsentinel.infrastructure.adapters.out.ai;

import com.logsentinel.application.ports.out.DiagnosticChatPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Adapter implementing {@link DiagnosticChatPort} with Spring AI's {@link ChatClient},
 * an abstraction over the active chat provider (Ollama by default, OpenAI under the
 * {@code openai} profile — selected via {@code spring.ai.model.chat}, LOG-CORE-INFRA-01).
 * <p>
 * Uses the streaming API ({@code .stream()}, never {@code .call()}) so the LLM response
 * is forwarded fragment by fragment as it arrives, per LOG-US3-BE-01. {@code Flux.toStream()}
 * bridges the reactive {@code Flux<String>} to a blocking {@link java.util.stream.Stream}
 * — safe here because the only caller ({@code StreamDiagnosticService}, annotated
 * {@code @Async}) already runs on a dedicated worker thread, never the HTTP request thread.
 */
@Component
public class SpringAiDiagnosticChatAdapter implements DiagnosticChatPort {

    private final ChatClient chatClient;

    public SpringAiDiagnosticChatAdapter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public void streamDiagnosis(String systemPrompt, String userPrompt, Consumer<String> onChunk) {
        chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content()
                .toStream()
                .forEach(onChunk);
    }
}
