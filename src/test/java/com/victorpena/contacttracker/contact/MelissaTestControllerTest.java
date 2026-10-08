package com.victorpena.contacttracker.contact;

import com.victorpena.contacttracker.controller.MelissaTestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MelissaTestControllerTest {
    private final MelissaPersonSearchClient client = mock(MelissaPersonSearchClient.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MelissaTestController(client))
                .setControllerAdvice(new MelissaExceptionHandler()).build();
    }

    @Test
    void directSearchFailureHasAnErrorBodyInsteadOfAnEmptySuccess() throws Exception {
        when(client.searchByName("Taylor Example")).thenThrow(new MelissaSearchException(
                "MELISSA_API_ERROR", "Product not enabled (GE08).", List.of("GE08")));
        mvc.perform(get("/api/melissa/search-by-name").param("name", "Taylor Example"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("MELISSA_API_ERROR"))
                .andExpect(jsonPath("$.resultCodes[0]").value("GE08"));
    }

    @Test
    void diagnosticEndpointReturnsSearchStatusAndPagination() throws Exception {
        when(client.searchDetailed("Taylor Example", "", "TX", "", "strict", 0))
                .thenReturn(new MelissaSearchResult("NO_EXACT_MATCH", "No exact match.",
                        Map.of("full", "Taylor Example", "state", "TX"), "strict", 0, 5,
                        "UE04", List.of("UE04"), 0, 0, 0, false, List.of()));
        mvc.perform(get("/api/melissa/diagnose").param("name", "Taylor Example").param("state", "TX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_EXACT_MATCH"))
                .andExpect(jsonPath("$.searchInputs.state").value("TX"))
                .andExpect(jsonPath("$.searchInputs.id").doesNotExist())
                .andExpect(jsonPath("$.totalRecords").value(0))
                .andExpect(jsonPath("$.morePagesAvailable").value(false));
    }

    @Test
    void invalidDiagnosticSettingsHaveABadRequestResponse() throws Exception {
        when(client.searchDetailed("Taylor Example", "", "", "", "invalid", 0))
                .thenThrow(new IllegalArgumentException("match must be strict or loose."));
        mvc.perform(get("/api/melissa/diagnose").param("name", "Taylor Example").param("match", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INPUT"));
    }
}
