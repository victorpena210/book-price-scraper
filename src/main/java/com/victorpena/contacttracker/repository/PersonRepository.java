package com.victorpena.contacttracker.repository;

import com.victorpena.contacttracker.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface PersonRepository
        extends JpaRepository<Person, Long> {

    @EntityGraph(attributePaths = "obituary")
    List<Person> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "obituary")
    List<Person> findAllByIdIn(List<Long> ids);

    List<Person> findByObituary_Id(
            Long obituaryId
    );

    void deleteByObituary_Id(
            Long obituaryId
    );

    /*
     * Find survivors for whom we do not
     * currently have a phone number.
     */
    List<Person> findByPhoneNumberIsNullOrderByIdAsc();
}
