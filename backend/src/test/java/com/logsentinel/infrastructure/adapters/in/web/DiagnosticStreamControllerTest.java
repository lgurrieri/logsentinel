package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.application.ports.in.StreamDiagnosticUseCase;
import com.logsentinel.application.ports.out.DiagnosticStreamListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test for {@link DiagnosticStreamController} (LOG-US3-BE-01). Only the
 * {@link StreamDiagnosticUseCase} is mocked — verifies the HTTP contract (status,
 * {@code text/event-stream} content type, chunk forwarding), never the streaming
 * logic itself (covered by {@code StreamDiagnosticServiceTest}).
 */
@WebMvcTest(DiagnosticStreamController.class)
class DiagnosticStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StreamDiagnosticUseCase streamDiagnosticUseCase;

    @Test
    @DisplayName("should return 200 with text/event-stream content type")
    void should_return_event_stream_content_type() throws Exception {
        UUID incidentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/incidents/{id}/diagnostic/stream", incidentId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/event-stream")));
    }

    @Test
    @DisplayName("should delegate to the use case with the path incident id")
    void should_delegate_to_use_case_with_incident_id() throws Exception {
        UUID incidentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/incidents/{id}/diagnostic/stream", incidentId))
                .andExpect(status().isOk());

        verify(streamDiagnosticUseCase).execute(eq(incidentId), any(DiagnosticStreamListener.class));
    }

    @Test
    @DisplayName("should forward each streamed chunk to the client as an SSE event")
    void should_forward_streamed_chunks_as_sse_events() throws Exception {
        UUID incidentId = UUID.randomUUID();
        willAnswer(invocation -> {
            DiagnosticStreamListener listener = invocation.getArgument(1);
            listener.onChunk("Root cause: ");
            listener.onChunk("pool exhaustion.");
            listener.onComplete(null);
            return null;
        }).given(streamDiagnosticUseCase).execute(eq(incidentId), any(DiagnosticStreamListener.class));

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/incidents/{id}/diagnostic/stream", incidentId))
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Root cause: ")))
                .andExpect(content().string(containsString("pool exhaustion.")));
    }
}
