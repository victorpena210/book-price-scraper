package com.victorpena.contacttracker.contact;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Personator Search codes, from Melissa's service and record result fields. */
final class MelissaResultCodes {
    private MelissaResultCodes() {
    }

    static List<String> parse(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> codes = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> code.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (codes.stream().anyMatch(code -> !code.matches("[A-Z]{2}[0-9]{2}"))) {
            throw new MelissaSearchException("UNEXPECTED_RESPONSE",
                    "Melissa returned an invalid result-code field.", List.of());
        }
        return codes;
    }

    static void requireCompletedSearch(List<String> codes) {
        for (String code : codes) {
            if (code.startsWith("GE") || code.startsWith("SE")
                    || (code.startsWith("UE") && !code.equals("UE01") && !code.equals("UE04"))
                    || code.equals("US03") || code.equals("GW11") || code.equals("GW12")) {
                throw new MelissaSearchException("MELISSA_API_ERROR", describe(code), codes);
            }
        }
    }

    static boolean reportsNoMatch(List<String> codes) {
        return codes.contains("UE01") || codes.contains("UE04");
    }

    static String status(List<String> codes, int returnedRecords) {
        if (returnedRecords > 0) {
            return codes.contains("US02") ? "POSSIBLE_MATCHES" : "MATCHES_FOUND";
        }
        return codes.contains("UE04") ? "NO_EXACT_MATCH" : "NO_MATCH";
    }

    private static String describe(String code) {
        return switch (code) {
            case "GE04" -> "Melissa received no API key (GE04).";
            case "GE05" -> "Melissa rejected the API key (GE05).";
            case "GE08" -> "The API key is not enabled for the requested Melissa product or level (GE08).";
            case "GE14" -> "Melissa reports that the account has no credits remaining (GE14).";
            case "UE02", "US03" -> "Melissa found too many matches. Add a known city/state or postal code (" + code + ").";
            case "UE03" -> "The requested Melissa page is out of range (UE03).";
            case "UE05" -> "Melissa rejected the state or postal code (UE05).";
            case "UE06" -> "Melissa's first-name or last-name length limit was exceeded (UE06).";
            case "GW11", "GW12" -> "Melissa rejected a search option or its value (" + code + ").";
            default -> "Melissa could not complete the lookup (" + code + ").";
        };
    }
}
