package com.victorpena.contacttracker.contact;

import com.victorpena.contacttracker.model.Person;
import com.victorpena.contacttracker.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PersonPhoneService {

    private final PersonRepository personRepository;

    public PersonPhoneService(
            PersonRepository personRepository
    ) {
        this.personRepository =
                personRepository;
    }

    /*
     * Show all survivors that currently
     * do not have a phone number.
     */
    @Transactional(readOnly = true)
    public List<PersonPhoneUpdateResult>
    getPeopleMissingPhones() {

        return personRepository
                .findByPhoneNumberIsNullOrderByIdAsc()
                .stream()
                .map(this::toResult)
                .toList();
    }

    /*
     * Save a phone number against ONE EXACT
     * Person database record.
     */
    @Transactional
    public PersonPhoneUpdateResult savePhoneNumber(
            Long personId,
            String phoneNumber
    ) {

        if (personId == null) {

            throw new IllegalArgumentException(
                    "personId is required."
            );
        }

        String cleanedPhone =
                cleanPhoneNumber(phoneNumber);

        /*
         * Load the exact survivor using their
         * primary key.
         */
        Person person =
                personRepository
                        .findById(personId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "No person exists with id "
                                                + personId
                                )
                        );

        /*
         * Attach the phone to THIS Person row.
         */
        person.setPhoneNumber(
                cleanedPhone
        );

        /*
         * UPDATE the row in MySQL.
         */
        Person savedPerson =
                personRepository.save(person);

        System.out.println(
                "PHONE SAVED"
                        + " | personId="
                        + savedPerson.getId()
                        + " | name="
                        + savedPerson.getFullName()
                        + " | phone="
                        + savedPerson.getPhoneNumber()
        );

        return toResult(savedPerson);
    }

    /*
     * Save multiple previously matched/
     * authorized results.
     */
    @Transactional
    public List<PersonPhoneUpdateResult>
    savePhoneNumbers(
            List<PersonPhoneUpdateRequest> updates
    ) {

        if (updates == null
                || updates.isEmpty()) {

            return List.of();
        }

        List<PersonPhoneUpdateResult> results =
                new ArrayList<>();

        for (PersonPhoneUpdateRequest update :
                updates) {

            if (update == null) {
                continue;
            }

            PersonPhoneUpdateResult result =
                    savePhoneNumber(
                            update.personId(),
                            update.phoneNumber()
                    );

            results.add(result);
        }

        return List.copyOf(results);
    }

    private PersonPhoneUpdateResult toResult(
            Person person
    ) {

        return new PersonPhoneUpdateResult(
                person.getId(),
                person.getFullName(),
                person.getRelationshipToDeceased(),
                person.getPhoneNumber()
        );
    }

    private String cleanPhoneNumber(
            String phoneNumber
    ) {

        if (phoneNumber == null
                || phoneNumber.isBlank()) {

            throw new IllegalArgumentException(
                    "phoneNumber is required."
            );
        }

        String cleaned =
                phoneNumber
                        .trim()
                        .replaceAll(
                                "[^0-9+]",
                                ""
                        );

        if (cleaned.length() < 7) {

            throw new IllegalArgumentException(
                    "Phone number appears invalid: "
                            + phoneNumber
            );
        }

        if (cleaned.length() > 32) {

            throw new IllegalArgumentException(
                    "Phone number is too long."
            );
        }

        return cleaned;
    }
}