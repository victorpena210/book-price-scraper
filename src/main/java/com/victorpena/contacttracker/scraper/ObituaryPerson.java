package com.victorpena.contacttracker.scraper;

import java.util.List;

public record ObituaryPerson(
        String name,
        String obituaryUrl,
        List<Survivor> survivors,
        boolean detailLoaded
) {

    public ObituaryPerson {
        survivors = List.copyOf(survivors);
    }

    /*
     * Listing result only.
     * We have not loaded the actual obituary yet.
     */
    public ObituaryPerson(
            String name,
            String obituaryUrl
    ) {
        this(
                name,
                obituaryUrl,
                List.of(),
                false
        );
    }

    /*
     * Successfully loaded obituary page.
     */
    public ObituaryPerson(
            String name,
            String obituaryUrl,
            List<Survivor> survivors
    ) {
        this(
                name,
                obituaryUrl,
                survivors,
                true
        );
    }
}