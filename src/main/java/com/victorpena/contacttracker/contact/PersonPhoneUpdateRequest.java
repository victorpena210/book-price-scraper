package com.victorpena.contacttracker.contact;

/**
 * A phone number that has already been obtained from an authorized source
 * and should be attached to one exact saved Person row.
 */
public record PersonPhoneUpdateRequest(
        Long personId,
        String phoneNumber
) {
}
