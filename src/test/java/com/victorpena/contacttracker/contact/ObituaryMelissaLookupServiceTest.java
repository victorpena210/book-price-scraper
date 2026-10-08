package com.victorpena.contacttracker.contact;

import com.victorpena.contacttracker.scraper.BookScraperClient;
import com.victorpena.contacttracker.scraper.ObituaryPerson;
import com.victorpena.contacttracker.scraper.Survivor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ObituaryMelissaLookupServiceTest {
    private final BookScraperClient scraper = mock(BookScraperClient.class);
    private final MelissaPersonSearchClient melissa = mock(MelissaPersonSearchClient.class);
    private final ObituaryMelissaLookupService service = new ObituaryMelissaLookupService(scraper, melissa);

    @Test
    void usesExplicitResidenceAndKeepsNameOnlyForMissingResidence() throws IOException {
        setSurvivors(new Survivor("Taylor Example", "child", "Example City", "TX"),
                new Survivor("Jordan Sample", "sibling"));
        service.lookupSurvivors(2);
        verify(melissa).searchByNameAndCityState("Taylor Example", "Example City", "TX");
        verify(melissa).searchByName("Jordan Sample");
        verifyNoMoreInteractions(melissa);
    }

    @Test
    void doesNotDiscardTheSameNameInADifferentLocation() throws IOException {
        setSurvivors(new Survivor("Taylor Example", "child", "Example City", "TX"),
                new Survivor("Taylor Example", "sibling", "Other City", "TX"),
                new Survivor("Taylor Example", "child", "EXAMPLE CITY", "tx"));
        assertThat(service.lookupSurvivors(5)).hasSize(2);
        verify(melissa).searchByNameAndCityState("Taylor Example", "Example City", "TX");
        verify(melissa).searchByNameAndCityState("Taylor Example", "Other City", "TX");
        verifyNoMoreInteractions(melissa);
    }

    @Test
    void preservesApiFailureMessageAndContinuesWithNextSurvivor() throws IOException {
        setSurvivors(new Survivor("Taylor Example", "child"), new Survivor("Jordan Sample", "sibling"));
        when(melissa.searchByName("Taylor Example")).thenThrow(new MelissaSearchException(
                "MELISSA_API_ERROR", "Melissa error GE08", List.of("GE08")));
        List<SurvivorContactLookup> results = service.lookupSurvivors(2);
        assertThat(results.get(0).error()).contains("GE08");
        assertThat(results.get(0).candidates()).isEmpty();
        assertThat(results.get(1).error()).isEmpty();
        verify(melissa).searchByName("Jordan Sample");
    }

    @Test
    void lookupLimitStillBoundsApiRequests() throws IOException {
        setSurvivors(new Survivor("Taylor Example", "child"), new Survivor("Jordan Sample", "sibling"));
        assertThat(service.lookupSurvivors(1)).hasSize(1);
        verify(melissa).searchByName("Taylor Example");
        verifyNoMoreInteractions(melissa);
    }

    private void setSurvivors(Survivor... survivors) throws IOException {
        when(scraper.scrapeObituaries()).thenReturn(List.of(new ObituaryPerson(
                "Example Deceased", "https://example.com/obituary", List.of(survivors))));
    }
}
