package com.victorpena.contacttracker.contact;

import com.victorpena.contacttracker.scraper.BookScraperClient;
import com.victorpena.contacttracker.scraper.ObituaryPerson;
import com.victorpena.contacttracker.scraper.Survivor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ObituaryMelissaLookupService {

    private static final int MAX_ALLOWED_LOOKUPS = 25;
    private static final long MELISSA_DELAY_MS = 250L;

    private final BookScraperClient bookScraperClient;
    private final MelissaPersonSearchClient melissaPersonSearchClient;

    public ObituaryMelissaLookupService(
            BookScraperClient bookScraperClient,
            MelissaPersonSearchClient melissaPersonSearchClient
    ) {
        this.bookScraperClient = bookScraperClient;
        this.melissaPersonSearchClient = melissaPersonSearchClient;
    }

    public List<SurvivorContactLookup> lookupSurvivors(int requestedLimit)
            throws IOException {

        int limit = Math.max(1, Math.min(requestedLimit, MAX_ALLOWED_LOOKUPS));
        List<ObituaryPerson> obituaries = bookScraperClient.scrapeObituaries();
        List<SurvivorContactLookup> results = new ArrayList<>();
        Set<LookupKey> completedQueries = new HashSet<>();

        for (ObituaryPerson obituary : obituaries) {
            for (Survivor survivor : obituary.survivors()) {
                if (results.size() >= limit) {
                    return List.copyOf(results);
                }

                String normalizedName = normalize(survivor.name());
                if (normalizedName.isBlank()) {
                    continue;
                }
                String city = normalize(survivor.residenceCity());
                String state = normalize(survivor.residenceState());

                // A survivor can appear in more than one obituary. Do not spend
                // another lookup credit on the same name AND locality in one run.
                // The same name in a different location is a different query.
                if (!completedQueries.add(new LookupKey(normalizedName, city, state))) {
                    continue;
                }

                try {
                    List<ContactCandidate> candidates;
                    if (!city.isBlank() || !state.isBlank()) {
                        candidates = melissaPersonSearchClient.searchByNameAndCityState(
                                survivor.name(), survivor.residenceCity(), survivor.residenceState());
                    } else {
                        candidates = melissaPersonSearchClient.searchByName(survivor.name());
                    }

                    results.add(new SurvivorContactLookup(
                            obituary.name(),
                            obituary.obituaryUrl(),
                            survivor.name(),
                            survivor.relationship(),
                            candidates,
                            ""
                    ));
                } catch (RuntimeException exception) {
                    // One Melissa lookup failing should not stop the whole batch.
                    results.add(new SurvivorContactLookup(
                            obituary.name(),
                            obituary.obituaryUrl(),
                            survivor.name(),
                            survivor.relationship(),
                            List.of(),
                            exception.getMessage()
                    ));
                }

                pauseBetweenMelissaRequests();
            }
        }

        return List.copyOf(results);
    }

    private void pauseBetweenMelissaRequests() {
        try {
            Thread.sleep(MELISSA_DELAY_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record LookupKey(String name, String city, String state) {
    }
}
