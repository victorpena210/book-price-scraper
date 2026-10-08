package com.victorpena.contacttracker.contact;

import java.util.List;
import java.util.Map;

/** Search diagnostics. The input map deliberately excludes the API key. */
public record MelissaSearchResult(
        String status,
        String message,
        Map<String, String> searchInputs,
        String searchConditions,
        int page,
        int recordsPerPage,
        String transmissionResults,
        List<String> resultCodes,
        int totalRecords,
        int totalPages,
        int returnedRecords,
        boolean morePagesAvailable,
        List<ContactCandidate> candidates
) {
    public MelissaSearchResult {
        searchInputs = Map.copyOf(searchInputs);
        resultCodes = List.copyOf(resultCodes);
        candidates = List.copyOf(candidates);
    }
}
