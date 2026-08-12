package com.logsentinel.application.service;

import com.logsentinel.application.ports.out.DiagnosticChatPort;
import com.logsentinel.application.ports.out.DiagnosticStreamListener;
import com.logsentinel.application.ports.out.IncidentDiagnosticRepository;
import com.logsentinel.application.ports.out.IncidentRepository;
import com.logsentinel.application.ports.out.RunbookSearchPort;
import com.logsentinel.domain.exception.IncidentNotFoundException;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentDiagnostic;
import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.RunbookChunk;
import com.logsentinel.domain.model.Urgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link StreamDiagnosticService} (LOG-US3-BE-01). Pure Mockito — no
 * Spring context, no real SseEmitter, no real ChatClient. Verifies the orchestration
 * logic only: incident lookup, runbook context augmentation, chunk forwarding and the
 * "listener.onComplete is always invoked exactly once" contract.
 */
@ExtendWith(MockitoExtension.class)
class StreamDiagnosticServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private RunbookSearchPort runbookSearchPort;

    @Mock
    private DiagnosticChatPort diagnosticChatPort;

    @Mock
    private IncidentDiagnosticRepository incidentDiagnosticRepository;

    @Mock
    private DiagnosticStreamListener listener;

    @InjectMocks
    private StreamDiagnosticService streamDiagnosticService;

    @Test
    @DisplayName("should forward every chunk from the chat port to the listener, in order, then complete without error")
    void should_forward_chunks_to_listener_when_incident_exists() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "payment-gw", Urgency.CRITICAL,
                "ERROR: connection pool exhausted", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(runbookSearchPort.findSimilarRunbooks(incident.getRawLogs()))
                .willReturn(List.of(new RunbookChunk(UUID.randomUUID(), "restart the pool on exhaustion")));
        willAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(2);
            onChunk.accept("Root cause: ");
            onChunk.accept("pool exhaustion.");
            return null;
        }).given(diagnosticChatPort).streamDiagnosis(anyString(), anyString(), any());

        streamDiagnosticService.execute(incidentId, listener);

        ArgumentCaptor<String> chunkCaptor = ArgumentCaptor.forClass(String.class);
        verify(listener, org.mockito.Mockito.times(2)).onChunk(chunkCaptor.capture());
        assertThat(chunkCaptor.getAllValues()).containsExactly("Root cause: ", "pool exhaustion.");
        verify(listener).onComplete(null);
    }

    @Test
    @DisplayName("should augment the system prompt with the runbook chunks retrieved for the incident")
    void should_augment_system_prompt_with_runbooks() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "auth-svc", Urgency.HIGH,
                "FATAL: token validation failed", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(runbookSearchPort.findSimilarRunbooks(incident.getRawLogs()))
                .willReturn(List.of(new RunbookChunk(UUID.randomUUID(), "rotate the signing key on token failures")));

        streamDiagnosticService.execute(incidentId, listener);

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(diagnosticChatPort).streamDiagnosis(systemPromptCaptor.capture(), userPromptCaptor.capture(), any());
        assertThat(systemPromptCaptor.getValue()).contains("rotate the signing key on token failures");
        assertThat(userPromptCaptor.getValue()).contains("FATAL: token validation failed");
    }

    @Test
    @DisplayName("should notify the listener with an error, never throw, when the incident does not exist")
    void should_notify_error_when_incident_not_found() {
        UUID incidentId = UUID.randomUUID();
        given(incidentRepository.findById(incidentId)).willReturn(Optional.empty());

        streamDiagnosticService.execute(incidentId, listener);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(listener).onComplete(errorCaptor.capture());
        assertThat(errorCaptor.getValue()).isInstanceOf(IncidentNotFoundException.class);
        verify(diagnosticChatPort, never()).streamDiagnosis(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should notify the listener with an error, never throw, when the chat port fails")
    void should_notify_error_when_chat_port_fails() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "billing-svc", Urgency.MEDIUM,
                "WARN: retry storm detected", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(runbookSearchPort.findSimilarRunbooks(anyString())).willReturn(List.of());
        RuntimeException chatFailure = new RuntimeException("LLM provider unavailable");
        willThrow(chatFailure).given(diagnosticChatPort).streamDiagnosis(anyString(), anyString(), any());

        streamDiagnosticService.execute(incidentId, listener);

        verify(listener).onComplete(eq(chatFailure));
    }

    @Test
    @DisplayName("should persist the full consolidated diagnostic text once the stream completes successfully (LOG-US3-DB-02)")
    void should_persist_consolidated_diagnostic_when_stream_completes_successfully() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "payment-gw", Urgency.CRITICAL,
                "ERROR: connection pool exhausted", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(runbookSearchPort.findSimilarRunbooks(incident.getRawLogs()))
                .willReturn(List.of(new RunbookChunk(UUID.randomUUID(), "restart the pool on exhaustion")));
        willAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(2);
            onChunk.accept("Root cause: ");
            onChunk.accept("pool exhaustion.");
            return null;
        }).given(diagnosticChatPort).streamDiagnosis(anyString(), anyString(), any());

        streamDiagnosticService.execute(incidentId, listener);

        ArgumentCaptor<IncidentDiagnostic> diagnosticCaptor = ArgumentCaptor.forClass(IncidentDiagnostic.class);
        verify(incidentDiagnosticRepository).save(diagnosticCaptor.capture());
        assertThat(diagnosticCaptor.getValue().getIncidentId()).isEqualTo(incidentId);
        assertThat(diagnosticCaptor.getValue().getDiagnosticText()).isEqualTo("Root cause: pool exhaustion.");
        assertThat(diagnosticCaptor.getValue().getSuggestedScript()).isNull();
        verify(listener).onComplete(null);
    }

    @Test
    @DisplayName("should extract the suggested script from the fully consolidated diagnostic text exactly once, "
            + "never fragment by fragment (LOG-US3-DB-02B)")
    void should_extract_suggested_script_from_consolidated_diagnostic_text() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "payment-gw", Urgency.CRITICAL,
                "ERROR: connection pool exhausted", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(runbookSearchPort.findSimilarRunbooks(incident.getRawLogs())).willReturn(List.of());
        willAnswer(invocation -> {
            // The opening fence is split across chunks on purpose: if extraction ran
            // per-fragment instead of on the consolidated text, neither fragment alone
            // would contain a well-formed fenced code block.
            Consumer<String> onChunk = invocation.getArgument(2);
            onChunk.accept("Root cause: pool exhaustion.\n\nRemediation:\n```bash\n");
            onChunk.accept("systemctl restart payment-gw\n");
            onChunk.accept("```\n");
            return null;
        }).given(diagnosticChatPort).streamDiagnosis(anyString(), anyString(), any());

        streamDiagnosticService.execute(incidentId, listener);

        ArgumentCaptor<IncidentDiagnostic> diagnosticCaptor = ArgumentCaptor.forClass(IncidentDiagnostic.class);
        verify(incidentDiagnosticRepository).save(diagnosticCaptor.capture());
        assertThat(diagnosticCaptor.getValue().getSuggestedScript()).isEqualTo("systemctl restart payment-gw");
    }

    @Test
    @DisplayName("should not persist any diagnostic when the incident does not exist")
    void should_not_persist_diagnostic_when_incident_not_found() {
        UUID incidentId = UUID.randomUUID();
        given(incidentRepository.findById(incidentId)).willReturn(Optional.empty());

        streamDiagnosticService.execute(incidentId, listener);

        verify(incidentDiagnosticRepository, never()).save(any());
    }

    @Test
    @DisplayName("should not persist any diagnostic when the chat port fails")
    void should_not_persist_diagnostic_when_chat_port_fails() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "billing-svc", Urgency.MEDIUM,
                "WARN: retry storm detected", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(runbookSearchPort.findSimilarRunbooks(anyString())).willReturn(List.of());
        willThrow(new RuntimeException("LLM provider unavailable"))
                .given(diagnosticChatPort).streamDiagnosis(anyString(), anyString(), any());

        streamDiagnosticService.execute(incidentId, listener);

        verify(incidentDiagnosticRepository, never()).save(any());
    }

    @Test
    @DisplayName("should still notify the listener successfully when persisting the diagnostic fails "
            + "(a persistence failure must not corrupt the already-streamed SSE response)")
    void should_still_complete_successfully_when_persisting_diagnostic_fails() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "payment-gw", Urgency.CRITICAL,
                "ERROR: connection pool exhausted", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(runbookSearchPort.findSimilarRunbooks(incident.getRawLogs())).willReturn(List.of());
        willThrow(new RuntimeException("DB unavailable")).given(incidentDiagnosticRepository).save(any());

        streamDiagnosticService.execute(incidentId, listener);

        verify(listener).onComplete(null);
    }

    @Test
    @DisplayName("should still persist the full consolidated diagnostic and suggestedScript when the listener "
            + "starts throwing mid-stream, simulating a dead SSE emitter after an AsyncRequestTimeoutException "
            + "or client disconnect (LOG-US3-BE-04)")
    void should_persist_diagnostic_when_listener_disconnects_mid_stream() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "payment-gw", Urgency.CRITICAL,
                "ERROR: connection pool exhausted", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(runbookSearchPort.findSimilarRunbooks(incident.getRawLogs())).willReturn(List.of());
        willAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(2);
            onChunk.accept("Root cause: pool exhaustion.\n```bash\n");
            onChunk.accept("systemctl restart payment-gw\n");
            onChunk.accept("```\n");
            return null;
        }).given(diagnosticChatPort).streamDiagnosis(anyString(), anyString(), any());
        // The 1st onChunk call succeeds (client still connected); every call from the
        // 2nd onward throws, simulating the SseEmitter already having timed out/closed.
        willDoNothing().willThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .given(listener).onChunk(anyString());

        streamDiagnosticService.execute(incidentId, listener);

        ArgumentCaptor<IncidentDiagnostic> diagnosticCaptor = ArgumentCaptor.forClass(IncidentDiagnostic.class);
        verify(incidentDiagnosticRepository).save(diagnosticCaptor.capture());
        assertThat(diagnosticCaptor.getValue().getDiagnosticText())
                .isEqualTo("Root cause: pool exhaustion.\n```bash\nsystemctl restart payment-gw\n```\n");
        assertThat(diagnosticCaptor.getValue().getSuggestedScript()).isEqualTo("systemctl restart payment-gw");
        verify(listener).onComplete(null);
    }

    @Test
    @DisplayName("should stop attempting to forward chunks to a listener once it has failed once, to avoid "
            + "retrying a connection already known to be dead (LOG-US3-BE-04)")
    void should_stop_forwarding_chunks_after_listener_first_fails() {
        UUID incidentId = UUID.randomUUID();
        Incident incident = new Incident(incidentId, "payment-gw", Urgency.CRITICAL,
                "ERROR: connection pool exhausted", IncidentStatus.OPEN, OffsetDateTime.now());
        given(incidentRepository.findById(incidentId)).willReturn(Optional.of(incident));
        given(runbookSearchPort.findSimilarRunbooks(incident.getRawLogs())).willReturn(List.of());
        willAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(2);
            onChunk.accept("chunk-1");
            onChunk.accept("chunk-2");
            onChunk.accept("chunk-3");
            return null;
        }).given(diagnosticChatPort).streamDiagnosis(anyString(), anyString(), any());
        willDoNothing().willThrow(new IllegalStateException("dead emitter"))
                .given(listener).onChunk(anyString());

        streamDiagnosticService.execute(incidentId, listener);

        verify(listener, org.mockito.Mockito.times(2)).onChunk(anyString());
    }
}
