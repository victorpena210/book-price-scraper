package com.victorpena.contacttracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "people")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "full_name",
            nullable = false,
            length = 255
    )
    private String fullName;

    @Column(
            name = "relationship_to_deceased",
            length = 100
    )
    private String relationshipToDeceased;

    @Column(
            name = "residence_city",
            length = 120
    )
    private String residenceCity;

    @Column(
            name = "residence_state",
            length = 100
    )
    private String residenceState;

    @Column(
            name = "phone_number",
            length = 32
    )
    private String phoneNumber;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "obituary_id",
            nullable = false
    )
    private Obituary obituary;

    public Person() {
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(
            String fullName
    ) {
        this.fullName = fullName;
    }

    public String getRelationshipToDeceased() {
        return relationshipToDeceased;
    }

    public void setRelationshipToDeceased(
            String relationshipToDeceased
    ) {
        this.relationshipToDeceased =
                relationshipToDeceased;
    }

    public String getResidenceCity() {
        return residenceCity;
    }

    public void setResidenceCity(
            String residenceCity
    ) {
        this.residenceCity =
                residenceCity;
    }

    public String getResidenceState() {
        return residenceState;
    }

    public void setResidenceState(
            String residenceState
    ) {
        this.residenceState =
                residenceState;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(
            String phoneNumber
    ) {
        this.phoneNumber =
                phoneNumber;
    }

    public Obituary getObituary() {
        return obituary;
    }

    public void setObituary(
            Obituary obituary
    ) {
        this.obituary =
                obituary;
    }
}