package com.victorpena.contacttracker.contact;

import java.util.List;

/** Run with Java 17 alone when Maven dependencies are unavailable. */
public class MelissaResultCodesChecks {
    public static void main(String[] args) {
        int checks = 0;
        for (String code : List.of("GE05", "GE08", "GE14", "SE01", "UE02", "UE03",
                "UE05", "UE06", "US03", "GW11", "GW12")) {
            try {
                MelissaResultCodes.requireCompletedSearch(List.of(code));
                throw new AssertionError(code + " was incorrectly treated as a completed search");
            } catch (MelissaSearchException exception) {
                require(exception.getResultCodes().equals(List.of(code)), "The failure code was lost");
                require(exception.getMessage().contains(code), "The failure message lost its code");
                checks++;
            }
        }
        List<String> noMatch = MelissaResultCodes.parse("UE01");
        MelissaResultCodes.requireCompletedSearch(noMatch);
        require(MelissaResultCodes.status(noMatch, 0).equals("NO_MATCH"), "UE01 must remain no match");
        checks++;

        List<String> noExactMatch = MelissaResultCodes.parse("UE04");
        MelissaResultCodes.requireCompletedSearch(noExactMatch);
        require(MelissaResultCodes.status(noExactMatch, 0).equals("NO_EXACT_MATCH"),
                "No exact match must remain distinguishable from no match");
        checks++;

        List<String> possible = MelissaResultCodes.parse("US02,VS12,GW01");
        MelissaResultCodes.requireCompletedSearch(possible);
        require(MelissaResultCodes.status(possible, 1).equals("POSSIBLE_MATCHES"),
                "A possible match must not become a confirmed match");
        checks++;

        List<String> success = MelissaResultCodes.parse(" us01, VR01,US01 ");
        MelissaResultCodes.requireCompletedSearch(success);
        require(success.equals(List.of("US01", "VR01")), "Result-code normalization failed");
        require(MelissaResultCodes.status(success, 1).equals("MATCHES_FOUND"), "A candidate was lost");
        checks++;

        try {
            MelissaResultCodes.requireCompletedSearch(List.of("US01", "GE08"));
            throw new AssertionError("A success code hid an API error");
        } catch (MelissaSearchException exception) {
            require(exception.getResultCodes().size() == 2, "Combined codes were lost");
            checks++;
        }

        try {
            MelissaResultCodes.parse("<html>unexpected response</html>");
            throw new AssertionError("Invalid response text was accepted as a code");
        } catch (MelissaSearchException exception) {
            require(exception.getError().equals("UNEXPECTED_RESPONSE"), "Wrong error category");
            checks++;
        }
        System.out.println("Passed " + checks + " Melissa result-code regression checks.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
