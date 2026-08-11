package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.config.TestcontainersConfiguration;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.Urgency;
import com.logsentinel.infrastructure.adapters.out.persistence.IncidentPersistenceAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * End-to-end integration test for the SSE diagnostic streaming endpoint
 * (LOG-US3-BE-01), exercising the full wiring: controller -&gt; use case -&gt;
 * {@code IncidentRepository} (real Postgres, Testcontainers) -&gt; {@code RunbookSearchPort}
 * (real Postgres, embedding model mocked to force the Full-Text fallback path, same
 * technique as {@code PgVectorRunbookSearchAdapterIntegrationTest}) -&gt;
 * {@code DiagnosticChatPort} ({@link ChatClient} mocked so the suite never depends on a
 * real running Ollama/OpenAI instance).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DiagnosticStreamIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private IncidentPersistenceAdapter incidentPersistenceAdapter;

    @MockitoBean
    private ChatClient chatClient;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.StreamResponseSpec streamResponseSpec;

    @BeforeEach
    void setUp() {
        // Force the RunbookSearchPort fallback path (real Full-Text query against the
        // real DB), never a real call to an embedding provider.
        given(embeddingModel.embed(anyString())).willThrow(new RuntimeException("no embedding provider in test"));

        requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        streamResponseSpec = org.mockito.Mockito.mock(ChatClient.StreamResponseSpec.class);
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.system(anyString())).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.stream()).willReturn(streamResponseSpec);
    }

    @Test
    @DisplayName("should stream the diagnostic as text/event-stream for an existing incident")
    void should_stream_diagnostic_for_existing_incident() {
        given(streamResponseSpec.content()).willReturn(
                Flux.just("Root cause: ", "connection pool exhaustion detected."));
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("payment-gw", Urgency.CRITICAL, "ERROR: connection pool exhausted"));

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/incidents/{id}/diagnostic/stream", String.class, incident.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getBody())
                .contains("Root cause: ")
                .contains("connection pool exhaustion detected.");
    }

    @Test
    @DisplayName("should return 404 when the incident does not exist (no bytes written yet, "
            + "so completeWithError re-dispatches to normal MVC exception handling)")
    void should_return_not_found_when_incident_does_not_exist() {
        UUID unknownId = UUID.randomUUID();

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/incidents/{id}/diagnostic/stream", String.class, unknownId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
