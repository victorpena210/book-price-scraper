ALTER TABLE people
    ADD COLUMN residence_city VARCHAR(120) NULL
        AFTER relationship_to_deceased,
    ADD COLUMN residence_state VARCHAR(100) NULL
        AFTER residence_city;