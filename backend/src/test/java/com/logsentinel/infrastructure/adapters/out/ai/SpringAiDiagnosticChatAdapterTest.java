package com.logsentinel.infrastructure.adapters.out.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link SpringAiDiagnosticChatAdapter} (LOG-US3-BE-01). Pure Mockito —
 * mocks Spring AI's fluent {@link ChatClient} chain directly; no Spring context, no
 * real network call to Ollama/OpenAI.
 */
@ExtendWith(MockitoExtension.class)
class SpringAiDiagnosticChatAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    @Test
    @DisplayName("should forward every fragment emitted by the streaming ChatClient, in order")
    void should_forward_every_streamed_fragment_in_order() {
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.system(anyString())).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.stream()).willReturn(streamResponseSpec);
        given(streamResponseSpec.content()).willReturn(Flux.just("Root cause: ", "connection pool exhausted."));

        var adapter = new SpringAiDiagnosticChatAdapter(chatClient);
        List<String> received = new ArrayList<>();

        adapter.streamDiagnosis("system prompt", "user prompt", received::add);

        assertThat(received).containsExactly("Root cause: ", "connection pool exhausted.");
        verify(requestSpec).system("system prompt");
        verify(requestSpec).user("user prompt");
    }

    @Test
    @DisplayName("should propagate a failure from the reactive stream to the caller")
    void should_propagate_stream_failure() {
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.system(anyString())).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.stream()).willReturn(streamResponseSpec);
        given(streamResponseSpec.content()).willReturn(Flux.error(new RuntimeException("LLM provider unavailable")));

        var adapter = new SpringAiDiagnosticChatAdapter(chatClient);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> adapter.streamDiagnosis("system prompt", "user prompt", chunk -> { }));
    }
}
