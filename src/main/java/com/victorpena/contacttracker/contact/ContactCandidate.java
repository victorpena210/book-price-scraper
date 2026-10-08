package com.victorpena.contacttracker.contact;

import java.util.List;

/**
 * A possible person returned by Melissa Personator Search.
 *
 * IMPORTANT:
 * A candidate is not automatically a confirmed identity match.
 */
public record ContactCandidate(
        String fullName,
        String addressLine1,
        String suite,
        String city,
        String state,
        String postalCode,
        String melissaIdentityKey,
        List<String> phoneNumbers
) {

    public ContactCandidate {
        phoneNumbers = phoneNumbers == null
                ? List.of()
                : List.copyOf(phoneNumbers);
    }

    public String formattedAddress() {

        StringBuilder result = new StringBuilder();

        if (addressLine1 != null && !addressLine1.isBlank()) {
            result.append(addressLine1);
        }

        if (suite != null && !suite.isBlank()) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(suite);
        }

        if (city != null && !city.isBlank()) {
            if (!result.isEmpty()) {
                result.append(", ");
            }
            result.append(city);
        }

        if (state != null && !state.isBlank()) {
            if (!result.isEmpty()) {
                result.append(", ");
            }
            result.append(state);
        }

        if (postalCode != null && !postalCode.isBlank()) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(postalCode);
        }

        return result.toString();
    }
}