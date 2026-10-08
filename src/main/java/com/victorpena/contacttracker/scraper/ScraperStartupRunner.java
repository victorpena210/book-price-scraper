package com.victorpena.contacttracker.scraper;

import com.victorpena.contacttracker.service.ObituaryPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Runs the obituary scraper once per application startup.
 */
@Component
public class ScraperStartupRunner implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(ScraperStartupRunner.class);

    private final BookScraperClient bookScraperClient;
    private final ObituaryPersistenceService obituaryPersistenceService;
    private final boolean runOnStartup;

    public ScraperStartupRunner(
            BookScraperClient bookScraperClient,
            ObituaryPersistenceService obituaryPersistenceService,
            @Value("${scraper.run-on-startup:true}")
            boolean runOnStartup
    ) {
        this.bookScraperClient = bookScraperClient;
        this.obituaryPersistenceService =
                obituaryPersistenceService;

        this.runOnStartup = runOnStartup;
    }

    @Override
    public void run(String... args) {

        if (!runOnStartup) {
            log.info(
                    "STARTUP SCRAPE DISABLED: " +
                    "Set scraper.run-on-startup=true to run it."
            );
            return;
        }

        log.info(
                "STARTUP SCRAPE STARTED: Loading obituary listing."
        );

        try {

            /*
             * STEP 1
             *
             * Scrape Legacy.com.
             *
             * This is where your 48 ObituaryPerson objects
             * are produced.
             */
            List<ObituaryPerson> people =
                    bookScraperClient.scrapeObituaries();

            if (people.isEmpty()) {
                log.warn(
                        "NO LISTING RECORDS FOUND."
                );

                return;
            }

            log.info(
                    "SCRAPE COMPLETE: {} obituary records found.",
                    people.size()
            );

            /*
             * STEP 2
             *
             * Take the Java objects returned by the scraper
             * and save them into MySQL.
             */
            ObituaryPersistenceService.SaveSummary summary =
                    obituaryPersistenceService.saveResults(people);

            /*
             * STEP 3
             *
             * Report what was actually saved.
             */
            log.info(
                    "DATABASE SAVE COMPLETE: {} obituaries and " +
                    "{} survivors saved.",
                    summary.obituariesSaved(),
                    summary.survivorsSaved()
            );

            log.info(
                    "STARTUP SCRAPE FINISHED."
            );

        } catch (IOException | RuntimeException exception) {

            log.error(
                    "STARTUP SCRAPE FAILED: " +
                    "The application is still running.",
                    exception
            );
        }
    }
}