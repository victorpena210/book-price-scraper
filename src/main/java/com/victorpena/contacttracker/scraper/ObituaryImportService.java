package com.victorpena.contacttracker.scraper;

import com.victorpena.contacttracker.service.ObituaryPersistenceService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jsoup.HttpStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** One bounded import at a time on this app instance; each obituary commits separately. */
@Service
public class ObituaryImportService {
    private static final Logger log = LoggerFactory.getLogger(ObituaryImportService.class);
    private final BookScraperClient scraper;
    private final ObituaryPersistenceService persistence;
    private final ExecutorService worker;
    private volatile Progress progress = new Progress("IDLE", BookScraperClient.AUSTIN_URL, 0, 0, 0, 0, 0,
            "Ready to import the Austin results page.", null, null);

    @Autowired
    public ObituaryImportService(BookScraperClient scraper, ObituaryPersistenceService persistence) {
        this(scraper, persistence, Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "obituary-import");
            thread.setDaemon(true);
            return thread;
        }));
    }

    ObituaryImportService(BookScraperClient scraper, ObituaryPersistenceService persistence, ExecutorService worker) {
        this.scraper = scraper;
        this.persistence = persistence;
        this.worker = worker;
    }

    public Progress status() { return progress; }

    public synchronized Progress start(String url) {
        String source = BookScraperClient.validateSource(url);
        if (progress.status().equals("RUNNING")) throw new IllegalStateException("An import is already running. Its progress is shown below.");
        progress = new Progress("RUNNING", source, 0, 0, 0, 0, 0, "Loading the Austin results page…", Instant.now(), null);
        try {
            worker.execute(() -> importPage(source));
        } catch (RuntimeException exception) {
            finish("FAILED", "The import could not start. Please try again after the app restarts.");
            throw exception;
        }
        return progress;
    }

    private void importPage(String source) {
        try {
            List<ObituaryPerson> listing = scraper.loadListing(source);
            if (listing.isEmpty()) throw new IOException("No obituary links were found on the Austin results page.");
            progress = new Progress("RUNNING", source, listing.size(), 0, 0, 0, 0,
                    "Found " + listing.size() + " obituaries. Reading survivor names…", progress.startedAt(), null);
            for (ObituaryPerson person : listing) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                ObituaryPerson result;
                int failed = 0;
                try {
                    result = scraper.loadDetail(person);
                } catch (HttpStatusException exception) {
                    // Stop on access denial or throttling; do not retry around the restriction.
                    if (exception.getStatusCode() == 401 || exception.getStatusCode() == 403 || exception.getStatusCode() == 429) throw exception;
                    result = person;
                    failed = 1;
                } catch (IOException exception) {
                    result = person;
                    failed = 1;
                }
                var saved = persistence.saveResults(List.of(result));
                Progress previous = progress;
                progress = new Progress("RUNNING", source, previous.total(), previous.processed() + 1,
                        previous.obituariesSaved() + saved.obituariesSaved(), previous.peopleSaved() + saved.survivorsSaved(),
                        previous.failedPages() + failed, "Reading and saving obituary " + (previous.processed() + 1) + " of " + previous.total() + "…",
                        previous.startedAt(), null);
                if (progress.processed() < progress.total()) Thread.sleep(500);
            }
            finish("COMPLETED", "Import finished: " + progress.obituariesSaved() + " obituaries and " + progress.peopleSaved()
                    + " survivor names saved or updated. " + (progress.failedPages() > 0 ? progress.failedPages() + " obituary pages could not be read. " : "")
                    + "Choose Test first name to try Melissa.");
        } catch (HttpStatusException exception) {
            finish("FAILED", "Legacy returned HTTP " + exception.getStatusCode()
                    + (exception.getStatusCode() == 429 ? " (rate limited). Please try later. " : " and did not allow this page to be read. ")
                    + "The import stopped. Any records already saved are kept.");
        } catch (IOException exception) {
            finish("FAILED", "Could not read the Legacy results page. " + exception.getMessage() + " Any records already saved are kept.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            finish("FAILED", "The import was interrupted. Saved records are kept; you can import again.");
        } catch (RuntimeException exception) {
            log.error("Obituary import failed: {}", exception.getClass().getSimpleName());
            finish("FAILED", "The import could not finish saving records. Previously saved records are kept. Check the app logs before retrying.");
        }
    }

    private void finish(String status, String message) {
        Progress previous = progress;
        progress = new Progress(status, previous.url(), previous.total(), previous.processed(), previous.obituariesSaved(),
                previous.peopleSaved(), previous.failedPages(), message, previous.startedAt(), Instant.now());
    }

    @PreDestroy public void close() { worker.shutdownNow(); }

    public record Progress(String status, String url, int total, int processed, int obituariesSaved,
                           int peopleSaved, int failedPages, String message, Instant startedAt, Instant completedAt) {}
}
