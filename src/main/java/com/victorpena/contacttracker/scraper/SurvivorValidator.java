package com.victorpena.contacttracker.scraper;

import java.util.Locale;
import java.util.Set;

public final class SurvivorValidator {

    private SurvivorValidator() {
        // Utility class - do not instantiate.
    }

    private static final Set<String> US_STATES = Set.of(
            "ALABAMA", "ALASKA", "ARIZONA", "ARKANSAS",
            "CALIFORNIA", "COLORADO", "CONNECTICUT", "DELAWARE",
            "FLORIDA", "GEORGIA", "HAWAII", "IDAHO",
            "ILLINOIS", "INDIANA", "IOWA", "KANSAS",
            "KENTUCKY", "LOUISIANA", "MAINE", "MARYLAND",
            "MASSACHUSETTS", "MICHIGAN", "MINNESOTA", "MISSISSIPPI",
            "MISSOURI", "MONTANA", "NEBRASKA", "NEVADA",
            "NEW HAMPSHIRE", "NEW JERSEY", "NEW MEXICO", "NEW YORK",
            "NORTH CAROLINA", "NORTH DAKOTA", "OHIO", "OKLAHOMA",
            "OREGON", "PENNSYLVANIA", "RHODE ISLAND",
            "SOUTH CAROLINA", "SOUTH DAKOTA", "TENNESSEE",
            "TEXAS", "UTAH", "VERMONT", "VIRGINIA",
            "WASHINGTON", "WEST VIRGINIA", "WISCONSIN", "WYOMING"
    );

    private static final Set<String> STATE_ABBREVIATIONS = Set.of(
            "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE",
            "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS",
            "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS",
            "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY",
            "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
            "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV",
            "WI", "WY", "DC"
    );

    private static final Set<String> NON_PERSON_VALUES = Set.of(
            "US NAVY",
            "U.S. NAVY",
            "NAVY",
            "ARMY",
            "AIR FORCE",
            "MARINES",
            "STEP",
            "STEP-",
            "MRS",
            "MR",
            "MS",
            "MISS",
            "DR",
            "THE"
    );

    public static boolean isValid(String name) {

        if (name == null || name.isBlank()) {
            return false;
        }

        String cleaned = name
                .replaceAll("\\s+", " ")
                .trim();

        String upper = cleaned.toUpperCase(Locale.ROOT);

        /*
         * Reject state names:
         * Texas
         * Missouri
         * New Mexico
         */
        if (US_STATES.contains(upper)) {
            return false;
        }

        /*
         * Reject state abbreviations:
         * TX
         * AL
         * SD
         */
        if (STATE_ABBREVIATIONS.contains(upper)) {
            return false;
        }

        /*
         * Reject obvious non-person values.
         */
        if (NON_PERSON_VALUES.contains(upper)) {
            return false;
        }

        /*
         * A person's name should not contain numbers.
         */
        if (cleaned.matches(".*\\d.*")) {
            return false;
        }

        /*
         * Reject malformed leftovers such as "Step-".
         */
        if (cleaned.endsWith("-")) {
            return false;
        }

        /*
         * Reject extremely short garbage.
         *
         * We still allow names like "Jo".
         */
        if (cleaned.length() < 2) {
            return false;
        }

        return true;
    }
}