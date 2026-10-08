package com.victorpena.contacttracker.repository;

import com.victorpena.contacttracker.model.Obituary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ObituaryRepository
        extends JpaRepository<Obituary, Long> {

    Optional<Obituary> findByObituaryUrl(String obituaryUrl);
}