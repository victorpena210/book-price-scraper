package com.victorpena.contacttracker.service;

import com.victorpena.contacttracker.model.Obituary;
import com.victorpena.contacttracker.model.Person;
import com.victorpena.contacttracker.repository.ObituaryRepository;
import com.victorpena.contacttracker.repository.PersonRepository;
import com.victorpena.contacttracker.scraper.ObituaryPerson;
import com.victorpena.contacttracker.scraper.Survivor;
import com.victorpena.contacttracker.scraper.SurvivorValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ObituaryPersistenceService {

    private final ObituaryRepository obituaryRepository;
    private final PersonRepository personRepository;

    public ObituaryPersistenceService(
            ObituaryRepository obituaryRepository,
            PersonRepository personRepository
    ) {
        this.obituaryRepository = obituaryRepository;
        this.personRepository = personRepository;
    }

    @Transactional
    public SaveSummary saveResults(
            List<ObituaryPerson> results
    ) {

        int obituariesSaved = 0;
        int survivorsSaved = 0;

        for (ObituaryPerson result : results) {

            /*
             * Find an obituary we previously saved.
             * The obituary URL is unique.
             */
            Obituary obituary =
                    obituaryRepository
                            .findByObituaryUrl(
                                    result.obituaryUrl()
                            )
                            .orElseGet(
                                    Obituary::new
                            );

            obituary.setDeceasedName(
                    result.name()
            );

            obituary.setObituaryUrl(
                    result.obituaryUrl()
            );

            /*
             * Save first so the obituary has an ID.
             */
            obituary =
                    obituaryRepository.save(
                            obituary
                    );

            obituariesSaved++;

            /*
             * If the individual obituary page did not load,
             * do not touch previously saved survivor data.
             */
            if (!result.detailLoaded()) {

                System.out.println(
                        "SKIPPING SURVIVOR REPLACEMENT: "
                                + result.name()
                                + " obituary page did not load."
                );

                continue;
            }

            /*
             * Load survivors already stored for this obituary.
             *
             * This matters because these Person records
             * may already have enrichment data attached,
             * such as phone numbers.
             */
            List<Person> existingPeople =
                    personRepository.findByObituary_Id(
                            obituary.getId()
                    );

            /*
             * Group existing Person records by normalized name.
             *
             * We intentionally do not include relationship
             * in the key.
             *
             * Relationship wording can change slightly
             * between parser runs, while the person is still
             * the same person.
             *
             * A queue lets us safely handle duplicate names.
             */
            Map<String, Deque<Person>> existingByKey =
                    new HashMap<>();

            for (Person existingPerson :
                    existingPeople) {

                String key =
                        buildPersonKey(
                                existingPerson
                                        .getFullName()
                        );

                existingByKey
                        .computeIfAbsent(
                                key,
                                ignored ->
                                        new ArrayDeque<>()
                        )
                        .addLast(
                                existingPerson
                        );
            }

            List<Person> peopleToSave =
                    new ArrayList<>();

            for (Survivor survivor :
                    result.survivors()) {

                if (!SurvivorValidator.isValid(
                        survivor.name()
                )) {

                    System.out.println(
                            "SKIPPING INVALID SURVIVOR: "
                                    + survivor.name()
                    );

                    continue;
                }

                String cleanedName =
                        survivor.name()
                                .replaceAll(
                                        "\\s+",
                                        " "
                                )
                                .trim();

                String relationship =
                        survivor.relationship();

                String key =
                        buildPersonKey(
                                cleanedName
                        );

                /*
                 * Try to reuse an existing Person record.
                 *
                 * If we reuse it, the database ID and any
                 * previously enriched data stay attached
                 * to the same person.
                 */
                Deque<Person> possibleMatches =
                        existingByKey.get(
                                key
                        );

                Person person;

                if (possibleMatches != null
                        && !possibleMatches.isEmpty()) {

                    person =
                            possibleMatches
                                    .removeFirst();

                    System.out.println(
                            "REUSING EXISTING PERSON: "
                                    + person.getId()
                                    + " "
                                    + cleanedName
                    );

                } else {

                    person =
                            new Person();

                    System.out.println(
                            "CREATING NEW PERSON: "
                                    + cleanedName
                    );
                }

                /*
                 * Update current scraped information.
                 */
                person.setFullName(
                        cleanedName
                );

                person.setRelationshipToDeceased(
                        relationship
                );

                /*
                 * Preserve explicitly stated residence
                 * information from the obituary.
                 *
                 * Example:
                 *
                 * Joe Gaddy of San Marcos, Texas
                 *
                 * residenceCity  = San Marcos
                 * residenceState = Texas
                 *
                 * We only overwrite the stored value when
                 * the latest scrape actually provides one.
                 * That way a later scrape with missing
                 * location text does not erase previously
                 * captured residence information.
                 */
                if (survivor.residenceCity() != null
                        && !survivor
                        .residenceCity()
                        .isBlank()) {

                    person.setResidenceCity(
                            survivor.residenceCity()
                    );
                }

                if (survivor.residenceState() != null
                        && !survivor
                        .residenceState()
                        .isBlank()) {

                    person.setResidenceState(
                            survivor.residenceState()
                    );
                }

                /*
                 * Notice that we intentionally do NOT call:
                 *
                 * person.setPhoneNumber(null);
                 *
                 * So an existing enriched phone number
                 * remains intact.
                 */

                person.setObituary(
                        obituary
                );

                peopleToSave.add(
                        person
                );
            }

            /*
             * Save new people and update existing people.
             */
            if (!peopleToSave.isEmpty()) {

                personRepository.saveAll(
                        peopleToSave
                );

                survivorsSaved +=
                        peopleToSave.size();
            }

            // Keep previously saved people when a later scrape omits them.
            // An incomplete page or parser change must not delete research records.

        }

        return new SaveSummary(
                obituariesSaved,
                survivorsSaved
        );
    }

    /*
     * Build a consistent key for matching a survivor
     * from the latest scrape to a Person already
     * stored in MySQL.
     */
    private String buildPersonKey(
            String fullName
    ) {

        return normalize(
                fullName
        );
    }

    private String normalize(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    public record SaveSummary(
            int obituariesSaved,
            int survivorsSaved
    ) {
    }
}