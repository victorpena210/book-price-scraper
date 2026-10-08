CREATE TABLE obituaries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    deceased_name VARCHAR(255) NOT NULL,
    obituary_url VARCHAR(700) NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_obituaries_url
        UNIQUE (obituary_url)
) ENGINE=InnoDB;

CREATE TABLE people (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(255) NOT NULL,
    relationship_to_deceased VARCHAR(100),
    phone_number VARCHAR(32),
    obituary_id BIGINT NOT NULL,

    PRIMARY KEY (id),

    INDEX idx_people_obituary_id (obituary_id),

    CONSTRAINT fk_people_obituary
        FOREIGN KEY (obituary_id)
        REFERENCES obituaries (id)
) ENGINE=InnoDB;