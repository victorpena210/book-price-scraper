package com.victorpena.contacttracker.scraper;

import com.victorpena.contacttracker.service.ObituaryPersistenceService;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.jsoup.HttpStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObituaryImportServiceTest {
    final BookScraperClient scraper = mock(BookScraperClient.class);
    final ObituaryPersistenceService persistence = mock(ObituaryPersistenceService.class);
    final ExecutorService worker = mock(ExecutorService.class);
    final AtomicReference<Runnable> task = new AtomicReference<>();
    final ObituaryImportService service = new ObituaryImportService(scraper, persistence, worker);
    final ObituaryPerson first = new ObituaryPerson("Alice Example", "https://www.legacy.com/us/obituaries/name/alice?id=1");
    final ObituaryPerson second = new ObituaryPerson("Bob Example", "https://www.legacy.com/us/obituaries/name/bob?id=2");

    @BeforeEach void prepare() throws Exception {
        doAnswer(call -> { task.set(call.getArgument(0)); return null; }).when(worker).execute(any());
        when(scraper.loadListing(BookScraperClient.AUSTIN_URL)).thenReturn(List.of(first, second));
        when(scraper.loadDetail(any())).thenAnswer(call -> call.getArgument(0));
        when(persistence.saveResults(any())).thenReturn(new ObituaryPersistenceService.SaveSummary(1, 0));
    }

    @Test void duplicateStartsAreRejectedAndEveryRecordIsCommittedSeparately() {
        assertThat(service.start(BookScraperClient.AUSTIN_URL).status()).isEqualTo("RUNNING");
        assertThatThrownBy(() -> service.start(BookScraperClient.AUSTIN_URL)).isInstanceOf(IllegalStateException.class);
        task.get().run();
        assertThat(service.status().status()).isEqualTo("COMPLETED");
        assertThat(service.status().processed()).isEqualTo(2);
        verify(persistence).saveResults(List.of(first));
        verify(persistence).saveResults(List.of(second));
        verify(worker, times(1)).execute(any());
        assertThat(service.start(BookScraperClient.AUSTIN_URL).status()).isEqualTo("RUNNING");
    }

    @Test void blockedListingStopsWithoutSavingOrRetrying() throws Exception {
        when(scraper.loadListing(anyString())).thenThrow(new HttpStatusException("Forbidden", 403, BookScraperClient.AUSTIN_URL));
        service.start(BookScraperClient.AUSTIN_URL); task.get().run();
        assertThat(service.status().status()).isEqualTo("FAILED");
        assertThat(service.status().message()).contains("HTTP 403");
        verifyNoInteractions(persistence);
        verify(scraper, never()).loadDetail(any());
    }

    @Test void throttlingStopsSubsequentRequestsButKeepsEarlierCommits() throws Exception {
        when(scraper.loadDetail(second)).thenThrow(new HttpStatusException("Too many requests", 429, second.obituaryUrl()));
        service.start(BookScraperClient.AUSTIN_URL); task.get().run();
        assertThat(service.status().status()).isEqualTo("FAILED");
        assertThat(service.status().processed()).isEqualTo(1);
        assertThat(service.status().message()).contains("429", "already saved");
        verify(persistence).saveResults(List.of(first));
        verify(persistence, never()).saveResults(List.of(second));
    }

    @Test void missingDetailPreservesListingAndReportsWarning() throws Exception {
        when(scraper.loadDetail(first)).thenThrow(new IOException("Timed out"));
        service.start(BookScraperClient.AUSTIN_URL); task.get().run();
        assertThat(service.status().status()).isEqualTo("COMPLETED");
        assertThat(service.status().failedPages()).isEqualTo(1);
        verify(persistence).saveResults(List.of(first));
    }

    @Test void rejectsUnsupportedSourceBeforeStartingAnyWork() {
        for (String url : List.of("http://127.0.0.1/", "https://www.legacy.com.attacker.test/us/obituaries/", BookScraperClient.AUSTIN_URL + "?url=http://localhost")) {
            assertThatThrownBy(() -> service.start(url)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(worker, scraper, persistence);
    }
}
