package com.victorpena.contacttracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "obituaries")
public class Obituary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deceased_name", nullable = false, length = 255)
    private String deceasedName;

    @Column(
            name = "obituary_url",
            nullable = false,
            unique = true,
            length = 700
    )
    private String obituaryUrl;

    public Obituary() {
    }

    public Long getId() {
        return id;
    }

    public String getDeceasedName() {
        return deceasedName;
    }

    public void setDeceasedName(String deceasedName) {
        this.deceasedName = deceasedName;
    }

    public String getObituaryUrl() {
        return obituaryUrl;
    }

    public void setObituaryUrl(String obituaryUrl) {
        this.obituaryUrl = obituaryUrl;
    }
}