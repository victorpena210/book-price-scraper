CREATE TABLE books (
    id BIGINT NOT NULL AUTO_INCREMENT,

    title VARCHAR(255) NOT NULL,
    product_url VARCHAR(700) NOT NULL,
    image_url VARCHAR(700),
    category VARCHAR(100),

    current_price DECIMAL(10, 2) NOT NULL,
    availability VARCHAR(100),
    rating TINYINT UNSIGNED,

    last_scraped_at DATETIME NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT uk_books_product_url
        UNIQUE (product_url),

    CONSTRAINT chk_books_rating
        CHECK (rating IS NULL OR rating BETWEEN 1 AND 5)
);

CREATE TABLE price_history (
    id BIGINT NOT NULL AUTO_INCREMENT,

    book_id BIGINT NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    availability VARCHAR(100),

    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT fk_price_history_book
        FOREIGN KEY (book_id)
        REFERENCES books (id)
        ON DELETE CASCADE,

    INDEX idx_price_history_book_recorded_at
        (book_id, recorded_at)
);

CREATE TABLE scrape_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,

    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,

    status VARCHAR(20) NOT NULL DEFAULT 'STARTED',

    books_found INT UNSIGNED NOT NULL DEFAULT 0,
    books_added INT UNSIGNED NOT NULL DEFAULT 0,
    books_updated INT UNSIGNED NOT NULL DEFAULT 0,

    error_message TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    CONSTRAINT chk_scrape_runs_status
        CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED'))
);