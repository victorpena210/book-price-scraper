package com.victorpena.contacttracker.scraper;

public record Survivor(
        String name,
        String relationship,
        String residenceCity,
        String residenceState
) {

    /*
     * Keeps all of our existing parser code working.
     *
     * Old code can still do:
     *
     * new Survivor("John Smith", "son")
     */
    public Survivor(
            String name,
            String relationship
    ) {
        this(
                name,
                relationship,
                null,
                null
        );
    }
}