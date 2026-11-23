-- =====================================================================
-- SmartDept - V4 : inscription (adresse confirmée, jetons), messages de contact, canal des e-mails
-- =====================================================================

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE AFTER email_notifications;

ALTER TABLE notifications ADD COLUMN email_channel VARCHAR(10) NULL AFTER email_status;
ALTER TABLE notifications ADD COLUMN email_info VARCHAR(255) NULL AFTER email_channel;

CREATE TABLE user_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    token_hash CHAR(64)     NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_token_user_type (user_id, type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE contact_messages (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    name         VARCHAR(120)  NOT NULL,
    email        VARCHAR(150)  NOT NULL,
    organisation VARCHAR(150),
    topic        VARCHAR(20)   NOT NULL,
    message      VARCHAR(3000) NOT NULL,
    handled      BOOLEAN       NOT NULL DEFAULT FALSE,
    handled_at   DATETIME(6),
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_contact_handled (handled, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
