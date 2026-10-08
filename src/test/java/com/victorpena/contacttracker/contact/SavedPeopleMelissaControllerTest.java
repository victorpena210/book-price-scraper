package com.victorpena.contacttracker.contact;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SavedPeopleMelissaControllerTest {
    @Test void readingNamesDoesNotTriggerLookupAndTestsRequireJsonPost() throws Exception {
        var reader = mock(SavedPeopleReader.class);
        var service = mock(SavedPeopleMelissaService.class);
        var client = mock(MelissaPersonSearchClient.class);
        when(reader.catalog()).thenReturn(new SavedPeopleReader.Catalog(1,1,List.of(SavedPeopleMelissaServiceTest.person(1,"Taylor Example","Austin"))));
        when(client.isConfigured()).thenReturn(true);
        var mvc = MockMvcBuilders.standaloneSetup(new SavedPeopleMelissaController(reader,service,client))
                .setControllerAdvice(new MelissaExceptionHandler()).build();
        mvc.perform(get("/api/people/melissa/records"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPeople").value(1))
                .andExpect(jsonPath("$.lookupVersion").value("2026-10-08.3"))
                .andExpect(jsonPath("$.apiKeyConfigured").value(true)).andExpect(jsonPath("$.apiKey").doesNotExist());
        verifyNoInteractions(service);
        mvc.perform(get("/api/people/melissa/test")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/people/melissa/test").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("personIds=1")).andExpect(status().isUnsupportedMediaType());
        when(service.test(List.of(1L))).thenReturn(new SavedPeopleMelissaService.BatchResult(List.of(),0,false,"Finished"));
        mvc.perform(post("/api/people/melissa/test").contentType(MediaType.APPLICATION_JSON).content("{\"personIds\":[1]}"))
                .andExpect(status().isOk());
        verify(service).test(List.of(1L));
    }
}
