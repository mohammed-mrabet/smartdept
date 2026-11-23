-- =====================================================================
-- SmartDept - V2 : offres de stage (API gratuites + offres du département), niveau d'études des filières
-- =====================================================================

ALTER TABLE filieres ADD COLUMN study_level VARCHAR(10) NULL AFTER level;

CREATE TABLE internship_offers (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    source          VARCHAR(30)   NOT NULL,
    external_id     VARCHAR(120)  NOT NULL,
    title           VARCHAR(255)  NOT NULL,
    company         VARCHAR(200),
    location        VARCHAR(200),
    country         VARCHAR(10)   NOT NULL,
    kind            VARCHAR(20)   NOT NULL,
    level_min       INT,
    level_max       INT,
    remote          BOOLEAN       NOT NULL DEFAULT FALSE,
    description     TEXT,
    url             VARCHAR(1000),
    salary          VARCHAR(150),
    skills          VARCHAR(500),
    duration_months INT,
    published_at    DATE,
    expires_at      DATE,
    contact_email   VARCHAR(150),
    posted_by_id    BIGINT,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    fetched_at      DATETIME(6),
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_offer_source_external UNIQUE (source, external_id),
    CONSTRAINT fk_offer_posted_by FOREIGN KEY (posted_by_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_offer_search (active, country, kind, published_at),
    INDEX idx_offer_level (level_min, level_max)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE offer_bookmarks (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id        BIGINT        NOT NULL,
    offer_id       BIGINT        NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    notes          VARCHAR(2000),
    applied_at     DATE,
    follow_up_sent BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_bookmark_user_offer UNIQUE (user_id, offer_id),
    CONSTRAINT fk_bookmark_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookmark_offer FOREIGN KEY (offer_id) REFERENCES internship_offers (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE offer_alerts (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    name             VARCHAR(100) NOT NULL,
    keywords         VARCHAR(200),
    country          VARCHAR(10),
    kind             VARCHAR(20),
    study_level      INT,
    remote_only      BOOLEAN      NOT NULL DEFAULT FALSE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    last_notified_at DATETIME(6),
    matches_sent     INT          NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_alert_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE offer_sync_runs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    source        VARCHAR(30)  NOT NULL,
    started_at    DATETIME(6)  NOT NULL,
    duration_ms   BIGINT       NOT NULL,
    fetched       INT          NOT NULL,
    created_count INT          NOT NULL,
    updated_count INT          NOT NULL,
    success       BOOLEAN      NOT NULL,
    message       VARCHAR(500),
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_sync_source (source, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
