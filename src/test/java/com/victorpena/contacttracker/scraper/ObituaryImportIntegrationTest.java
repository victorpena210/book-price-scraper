package com.victorpena.contacttracker.scraper;

import com.victorpena.contacttracker.repository.ObituaryRepository;
import com.victorpena.contacttracker.repository.PersonRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ObituaryImportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObituaryImportService imports;
    @Autowired PersonRepository people;
    @Autowired ObituaryRepository obituaries;
    @MockitoBean BookScraperClient scraper;
    static final String BODY = "{\"url\":\"" + BookScraperClient.AUSTIN_URL + "\"}";

    @Test void securedImportPersistsNamesAndRepeatedImportPreservesIdsAndPhoneNumbers() throws Exception {
        people.deleteAll(); obituaries.deleteAll();
        var listing = new ObituaryPerson("Alice Example", "https://www.legacy.com/us/obituaries/name/alice?id=1");
        when(scraper.loadListing(BookScraperClient.AUSTIN_URL)).thenReturn(List.of(listing));
        when(scraper.loadDetail(listing)).thenReturn(new ObituaryPerson(listing.name(), listing.obituaryUrl(), List.of(new Survivor("Taylor Example", "son", "Austin", "Texas"))));

        mvc.perform(get("/api/obituaries/import")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/obituaries/import").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/obituaries/import").with(user("clay@example.test")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/obituaries/import").with(user("clay@example.test")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"http://localhost\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(scraper);

        startAndWait();
        assertThat(obituaries.count()).isEqualTo(1);
        assertThat(people.count()).isEqualTo(1);
        var saved = people.findAll().get(0);
        Long personId = saved.getId();
        saved.setPhoneNumber("2025550100"); people.save(saved);
        startAndWait();
        assertThat(obituaries.count()).isEqualTo(1);
        assertThat(people.count()).isEqualTo(1);
        assertThat(people.findById(personId).orElseThrow().getPhoneNumber()).isEqualTo("2025550100");
        mvc.perform(get("/api/obituaries/import").with(user("clay@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.peopleSaved").value(1));
    }

    private void startAndWait() throws Exception {
        mvc.perform(post("/api/obituaries/import").with(user("clay@example.test")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isAccepted());
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (imports.status().status().equals("RUNNING") && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(imports.status().status()).isEqualTo("COMPLETED");
    }
}
