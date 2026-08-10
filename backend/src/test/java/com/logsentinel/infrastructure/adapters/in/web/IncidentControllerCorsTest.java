package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.application.ports.in.CreateIncidentUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the frontend's dev origin is allowed to call the incidents API
 * cross-origin. Uses a full Spring MVC test slice (not standalone MockMvc) so that
 * any registered WebMvcConfigurer CORS mappings are actually applied.
 */
@WebMvcTest(IncidentController.class)
class IncidentControllerCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateIncidentUseCase createIncidentUseCase;

    @Test
    @DisplayName("should allow cross-origin preflight requests from the frontend dev origin")
    void should_allow_preflight_from_frontend_dev_origin() throws Exception {
        mockMvc.perform(options("/api/v1/incidents")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
