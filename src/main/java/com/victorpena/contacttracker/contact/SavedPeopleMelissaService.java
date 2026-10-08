package com.victorpena.contacttracker.contact;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads existing Person rows; never scrapes, deletes, or auto-saves a candidate. */
@Service
public class SavedPeopleMelissaService {
    private final SavedPeopleReader reader;
    private final MelissaPersonSearchClient client;
    private final ReentrantLock lock = new ReentrantLock();

    public SavedPeopleMelissaService(SavedPeopleReader reader, MelissaPersonSearchClient client) {
        this.reader = reader;
        this.client = client;
    }

    public BatchResult test(List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty() || requestedIds.size() > 25
                || requestedIds.stream().anyMatch(id -> id == null || id < 1)) {
            throw new IllegalArgumentException("Choose between 1 and 25 saved person IDs per request.");
        }
        if (!client.isConfigured()) {
            throw new MelissaSearchException("CONFIGURATION_ERROR",
                    "Set MELISSA_API_KEY in your app's run configuration and restart the app.", List.of());
        }
        if (!lock.tryLock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A saved-name test is already running.");
        }
        try {
            List<Long> ids = requestedIds.stream().distinct().toList();
            var saved = reader.selected(ids).stream().collect(
                    Collectors.toMap(SavedPeopleReader.SavedPerson::personId, Function.identity()));
            if (!saved.keySet().containsAll(ids)) {
                throw new IllegalArgumentException("One or more saved people no longer exist. Reload the list.");
            }
            var results = new ArrayList<PersonResult>();
            var completedQueries = new HashMap<List<String>, PersonResult>();
            int requests = 0;
            for (Long id : ids) {
                var person = saved.get(id);
                var key = List.of(normalize(person.fullName()), normalize(person.residenceCity()),
                        normalize(person.residenceState()));
                var previous = completedQueries.get(key);
                if (previous != null) {
                    results.add(new PersonResult(person, previous.lookup(), previous.error(),
                            previous.message(), previous.resultCodes(), true));
                    continue;
                }
                if (requests > 0) {
                    try { Thread.sleep(250); }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return new BatchResult(results, requests, true, "Test stopped before the next request.");
                    }
                }
                PersonResult result;
                try {
                    if (normalize(person.fullName()).isBlank()) {
                        throw new IllegalArgumentException("The saved record has no name.");
                    }
                    requests++;
                    var lookup = client.searchDetailed(person.fullName(), person.residenceCity(),
                            person.residenceState(), "", "strict", 0);
                    result = new PersonResult(person, lookup, "", lookup.message(), lookup.resultCodes(), false);
                } catch (MelissaSearchException exception) {
                    result = new PersonResult(person, null, exception.getError(), exception.getMessage(),
                            exception.getResultCodes(), false);
                    if (mustStop(exception)) {
                        results.add(result);
                        return new BatchResult(results, requests, true, exception.getMessage());
                    }
                } catch (IllegalArgumentException exception) {
                    result = new PersonResult(person, null, "INVALID_INPUT", exception.getMessage(), List.of(), false);
                }
                results.add(result);
                completedQueries.put(key, result);
            }
            return new BatchResult(results, requests, false, "Saved-name test finished.");
        } finally {
            lock.unlock();
        }
    }

    private static boolean mustStop(MelissaSearchException exception) {
        // Record-specific errors can continue; account, option and service errors stop the run.
        return !exception.getError().equals("MELISSA_API_ERROR")
                || exception.getResultCodes().isEmpty()
                || exception.getResultCodes().stream().anyMatch(code ->
                        code.startsWith("GE") || code.startsWith("SE") || code.startsWith("GW")
                                || code.equals("UE03"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public record PersonResult(SavedPeopleReader.SavedPerson person, MelissaSearchResult lookup,
            String error, String message, List<String> resultCodes, boolean reusedResult) { }
    public record BatchResult(List<PersonResult> results, int apiRequests, boolean stopped, String message) { }
}
