package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.application.ports.in.CreateIncidentUseCase;
import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit test for IncidentController using standalone MockMvc setup.
 * Tests JSR-380 validation and HTTP response codes without Spring context.
 */
@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CreateIncidentUseCase createIncidentUseCase;

    @BeforeEach
    void setUp() {
        IncidentController controller = new IncidentController(createIncidentUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("should return 201 with incident data when request is valid")
    void should_return_201_when_valid_request() throws Exception {
        // Arrange
        UUID incidentId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        var incident = new Incident(incidentId, "payment-gw", Urgency.CRITICAL,
                "ERROR: pool exhausted at 2024-01-15T10:30:00Z", IncidentStatus.OPEN, createdAt);
        given(createIncidentUseCase.execute(any())).willReturn(incident);

        String validJson = """
                {
                    "systemName": "payment-gw",
                    "urgency": "CRITICAL",
                    "rawLogSnapshot": "ERROR: pool exhausted at 2024-01-15T10:30:00Z"
                }
                """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(incidentId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.systemName").value("payment-gw"))
                .andExpect(jsonPath("$.urgency").value("CRITICAL"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("should return 400 when systemName is blank")
    void should_return_400_when_system_name_blank() throws Exception {
        String invalidJson = """
                {
                    "systemName": "",
                    "urgency": "HIGH",
                    "rawLogSnapshot": "ERROR: some error that is long enough"
                }
                """;

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("should return 400 when rawLogSnapshot is too short")
    void should_return_400_when_raw_log_snapshot_too_short() throws Exception {
        String invalidJson = """
                {
                    "systemName": "payment-gw",
                    "urgency": "CRITICAL",
                    "rawLogSnapshot": "short"
                }
                """;

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("rawLogSnapshot"));
    }

    @Test
    @DisplayName("should return 400 when rawLogSnapshot is null")
    void should_return_400_when_raw_log_snapshot_null() throws Exception {
        String invalidJson = """
                {
                    "systemName": "payment-gw",
                    "urgency": "CRITICAL"
                }
                """;

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("should return 400 when urgency is null")
    void should_return_400_when_urgency_null() throws Exception {
        String invalidJson = """
                {
                    "systemName": "payment-gw",
                    "rawLogSnapshot": "ERROR: pool exhausted at 2024-01-15T10:30:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("should return 400 when urgency has invalid enum value")
    void should_return_400_when_urgency_invalid_enum() throws Exception {
        String invalidJson = """
                {
                    "systemName": "payment-gw",
                    "urgency": "SUPER_DUPER_CRITICAL",
                    "rawLogSnapshot": "ERROR: pool exhausted at 2024-01-15T10:30:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed Request"));
    }

    @Test
    @DisplayName("should return 400 when request body is empty")
    void should_return_400_when_body_empty() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should not expose stacktrace in error response")
    void should_not_expose_stacktrace_in_error_response() throws Exception {
        // Arrange - simulate unexpected exception from use case
        given(createIncidentUseCase.execute(any())).willThrow(new RuntimeException("DB connection lost"));

        String validJson = """
                {
                    "systemName": "payment-gw",
                    "urgency": "CRITICAL",
                    "rawLogSnapshot": "ERROR: pool exhausted at 2024-01-15T10:30:00Z"
                }
                """;

        // Act & Assert - should return generic 500, not expose the exception message
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
