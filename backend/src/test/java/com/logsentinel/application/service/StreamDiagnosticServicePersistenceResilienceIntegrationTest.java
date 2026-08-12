package com.logsentinel.application.service;

import com.logsentinel.application.ports.out.DiagnosticChatPort;
import com.logsentinel.application.ports.out.DiagnosticStreamListener;
import com.logsentinel.application.ports.out.RunbookSearchPort;
import com.logsentinel.config.TestcontainersConfiguration;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentDiagnostic;
import com.logsentinel.domain.model.Urgency;
import com.logsentinel.infrastructure.adapters.out.persistence.IncidentDiagnosticPersistenceAdapter;
import com.logsentinel.infrastructure.adapters.out.persistence.IncidentPersistenceAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

/**
 * Integration test (Testcontainers, real Postgres — same pattern as
 * {@code IncidentDiagnosticPersistenceAdapterIntegrationTest}) reproducing the
 * production bug fixed by LOG-US3-BE-04.
 * <p>
 * Verified live in the Azure VM: {@code spring.mvc.async.request-timeout} was never
 * configured explicitly (implicit Spring MVC default, 30s), and Ollama took longer
 * than that to finish generating a diagnostic. Spring aborted the async request with
 * {@code AsyncRequestTimeoutException} before {@code StreamDiagnosticService}'s
 * {@code finally} block ran, and once the {@code SseEmitter} is aborted, any further
 * {@code emitter.send(...)} call throws {@code IllegalStateException} — which, prior
 * to this fix, propagated out of {@code Stream.forEach} in
 * {@code SpringAiDiagnosticChatAdapter}, aborted the whole chat stream consumption,
 * and skipped {@code persistDiagnostic(...)} entirely (gated by {@code error == null}).
 * Net result verified with {@code SELECT count(*) FROM incident_diagnostics} in
 * production: 0 rows after two full stream runs.
 * <p>
 * This test reproduces that exact failure mode with a {@link DiagnosticStreamListener}
 * test double that starts throwing {@link IllegalStateException} from the 2nd chunk
 * onward (simulating the SSE emitter dying mid-stream) while the mocked
 * {@link DiagnosticChatPort} keeps emitting chunks — exactly as Ollama would keep
 * generating tokens on the server side, oblivious to the dead client connection.
 * The fully consolidated diagnostic text and its derived {@code suggestedScript} must
 * still end up persisted in {@code incident_diagnostics}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StreamDiagnosticServicePersistenceResilienceIntegrationTest {

    @Autowired
    private StreamDiagnosticService streamDiagnosticService;

    @Autowired
    private IncidentPersistenceAdapter incidentPersistenceAdapter;

    @Autowired
    private IncidentDiagnosticPersistenceAdapter incidentDiagnosticPersistenceAdapter;

    @MockitoBean
    private DiagnosticChatPort diagnosticChatPort;

    @MockitoBean
    private RunbookSearchPort runbookSearchPort;

    @Test
    @DisplayName("should persist the fully consolidated diagnostic and its suggestedScript even when the "
            + "SSE listener starts throwing (simulating AsyncRequestTimeoutException / client disconnect) "
            + "before the LLM finishes emitting every chunk (LOG-US3-BE-04)")
    void should_persist_diagnostic_when_listener_disconnects_mid_stream() throws InterruptedException {
        Incident incident = incidentPersistenceAdapter.save(
                Incident.createNew("payment-gw-resilience", Urgency.CRITICAL, "ERROR: connection pool exhausted"));
        given(runbookSearchPort.findSimilarRunbooks(anyString())).willReturn(List.of());
        willAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(2);
            // Simulate Ollama continuing to generate tokens well after the client
            // connection is dead: 1 chunk arrives before the "disconnect", 3 more
            // arrive after — none of them may be lost.
            onChunk.accept("Root cause: pool exhaustion.\n\nRemediation:\n```bash\n");
            onChunk.accept("systemctl restart payment-gw\n");
            onChunk.accept("```\n");
            onChunk.accept("Verify with systemctl status payment-gw.");
            return null;
        }).given(diagnosticChatPort).streamDiagnosis(anyString(), anyString(), any());

        CountDownLatch completed = new CountDownLatch(1);
        DiagnosticStreamListener disconnectingListener = new DiagnosticStreamListener() {
            private int chunkCount = 0;

            @Override
            public void onChunk(String textFragment) {
                chunkCount++;
                if (chunkCount >= 2) {
                    // From the 2nd chunk onward, the emitter is already dead — this is
                    // exactly what SseEmitter#send throws (IllegalStateException) once
                    // Spring has already completed the response following an
                    // AsyncRequestTimeoutException or a client-initiated disconnect.
                    throw new IllegalStateException("ResponseBodyEmitter has already completed");
                }
            }

            @Override
            public void onComplete(Throwable error) {
                completed.countDown();
            }
        };

        streamDiagnosticService.execute(incident.getId(), disconnectingListener);

        assertThat(completed.await(5, TimeUnit.SECONDS))
                .as("StreamDiagnosticService.execute must notify onComplete exactly once, "
                        + "even when the listener itself is failing")
                .isTrue();
        Optional<IncidentDiagnostic> persisted =
                incidentDiagnosticPersistenceAdapter.findByIncidentId(incident.getId());
        assertThat(persisted)
                .as("the diagnostic must be persisted despite the listener/emitter dying mid-stream")
                .isPresent();
        assertThat(persisted.get().getDiagnosticText())
                .isEqualTo("Root cause: pool exhaustion.\n\nRemediation:\n```bash\n"
                        + "systemctl restart payment-gw\n"
                        + "```\n"
                        + "Verify with systemctl status payment-gw.");
        assertThat(persisted.get().getSuggestedScript()).isEqualTo("systemctl restart payment-gw");
    }
}
