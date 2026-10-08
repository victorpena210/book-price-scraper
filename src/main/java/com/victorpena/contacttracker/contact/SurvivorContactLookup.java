package com.victorpena.contacttracker.contact;

import java.util.List;

/**
 * One obituary survivor and the possible Melissa records returned for that name.
 */
public record SurvivorContactLookup(
        String deceasedName,
        String obituaryUrl,
        String survivorName,
        String relationship,
        List<ContactCandidate> candidates,
        String error
) {
    public SurvivorContactLookup {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        error = error == null ? "" : error;
    }
}
