package com.victorpena.contacttracker.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victorpena.contacttracker.model.ScrapeRun;

public interface ScrapeRunRepository extends JpaRepository<ScrapeRun, Long>{

}
