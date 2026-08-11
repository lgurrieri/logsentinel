package com.logsentinel.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the single {@link ChatClient} bean used across the application from the
 * auto-configured {@link ChatClient.Builder} (Spring AI provides the builder for
 * whichever chat provider is active — Ollama by default, OpenAI under the
 * {@code openai} profile, selected via {@code spring.ai.model.chat}). Spring Boot's
 * autoconfiguration only exposes the builder, not a ready-made {@code ChatClient}
 * bean, so this small configuration class fills that gap (LOG-US3-BE-01).
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder.build();
    }
}
