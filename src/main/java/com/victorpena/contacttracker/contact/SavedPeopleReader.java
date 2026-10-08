package com.victorpena.contacttracker.contact;

import com.victorpena.contacttracker.model.Person;
import com.victorpena.contacttracker.repository.PersonRepository;
import com.victorpena.contacttracker.repository.ObituaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Finish database reads before making any external lookup request. */
@Service
public class SavedPeopleReader {
    private final PersonRepository people;
    private final ObituaryRepository obituaries;

    public SavedPeopleReader(PersonRepository people, ObituaryRepository obituaries) {
        this.people = people;
        this.obituaries = obituaries;
    }

    @Transactional(readOnly = true)
    public Catalog catalog() {
        var rows = people.findAllByOrderByIdAsc().stream().map(SavedPeopleReader::snapshot).toList();
        return new Catalog(rows.size(), obituaries.count(), rows);
    }

    @Transactional(readOnly = true)
    public List<SavedPerson> selected(List<Long> ids) {
        return people.findAllByIdIn(ids).stream().map(SavedPeopleReader::snapshot).toList();
    }

    private static SavedPerson snapshot(Person person) {
        var obituary = person.getObituary();
        return new SavedPerson(person.getId(), person.getFullName(), person.getRelationshipToDeceased(),
                person.getResidenceCity(), person.getResidenceState(), person.getPhoneNumber(),
                obituary.getDeceasedName(), obituary.getObituaryUrl());
    }

    public record SavedPerson(Long personId, String fullName, String relationship,
            String residenceCity, String residenceState, String phoneNumber,
            String deceasedName, String obituaryUrl) { }
    public record Catalog(long totalPeople, long totalObituaries, List<SavedPerson> people) { }
}
