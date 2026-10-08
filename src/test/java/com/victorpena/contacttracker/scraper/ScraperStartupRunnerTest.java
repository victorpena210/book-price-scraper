package com.victorpena.contacttracker.scraper;

import com.victorpena.contacttracker.service.ObituaryPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ScraperStartupRunnerTest {

    private final BookScraperClient client =
            mock(BookScraperClient.class);

    private final ObituaryPersistenceService persistenceService =
            mock(ObituaryPersistenceService.class);

    @Test
    void runsScraperAndSavesResults(CapturedOutput output)
            throws IOException {

        when(client.scrapeObituaries()).thenReturn(
                List.of(
                        new ObituaryPerson(
                                "Example Person",
                                "https://example.com/obituary"
                        )
                )
        );

        when(persistenceService.saveResults(anyList()))
                .thenReturn(
                        new ObituaryPersistenceService.SaveSummary(
                                1,
                                0
                        )
                );

        ScraperStartupRunner runner =
                new ScraperStartupRunner(
                        client,
                        persistenceService,
                        true
                );

        runner.run();

        verify(client).scrapeObituaries();
        verify(persistenceService).saveResults(anyList());

        assertThat(output.getAll())
                .contains("STARTUP SCRAPE STARTED")
                .contains("SCRAPE COMPLETE: 1 obituary records found.")
                .contains("DATABASE SAVE COMPLETE: 1 obituaries and 0 survivors saved.")
                .contains("STARTUP SCRAPE FINISHED.");
    }

    @Test
    void disabledRunnerMakesNoRequests(CapturedOutput output) {

        ScraperStartupRunner runner =
                new ScraperStartupRunner(
                        client,
                        persistenceService,
                        false
                );

        runner.run();

        verifyNoInteractions(client);
        verifyNoInteractions(persistenceService);

        assertThat(output.getAll())
                .contains("STARTUP SCRAPE DISABLED")
                .doesNotContain("STARTUP SCRAPE STARTED");
    }

    @Test
    void emptyListingProducesAWarning(CapturedOutput output)
            throws IOException {

        when(client.scrapeObituaries())
                .thenReturn(List.of());

        ScraperStartupRunner runner =
                new ScraperStartupRunner(
                        client,
                        persistenceService,
                        true
                );

        runner.run();

        verify(client).scrapeObituaries();
        verifyNoInteractions(persistenceService);

        assertThat(output.getAll())
                .contains("NO LISTING RECORDS FOUND.")
                .doesNotContain("DATABASE SAVE COMPLETE");
    }

    @Test
    void failedRequestDoesNotAbortApplicationStartup(
            CapturedOutput output
    ) throws IOException {

        when(client.scrapeObituaries())
                .thenThrow(
                        new IOException(
                                "Simulated connection timeout"
                        )
                );

        ScraperStartupRunner runner =
                new ScraperStartupRunner(
                        client,
                        persistenceService,
                        true
                );

        assertThatCode(runner::run)
                .doesNotThrowAnyException();

        verifyNoInteractions(persistenceService);

        assertThat(output.getAll())
                .contains("STARTUP SCRAPE FAILED")
                .contains("Simulated connection timeout")
                .doesNotContain("STARTUP SCRAPE FINISHED");
    }
}