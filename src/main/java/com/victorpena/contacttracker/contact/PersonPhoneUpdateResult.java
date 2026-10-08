package com.victorpena.contacttracker.contact;

/**
 * The saved state of one Person after a phone-number update.
 */
public record PersonPhoneUpdateResult(
        Long personId,
        String fullName,
        String relationship,
        String phoneNumber
) {
}
