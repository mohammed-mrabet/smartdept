-- =====================================================================
-- SmartDept - V1 : schéma initial (TP5 : versionnement de la base avec Flyway)
-- Une migration ne se modifie jamais une fois appliquée : toute évolution = nouveau fichier V2__..., V3__...
-- =====================================================================

CREATE TABLE filieres (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    level       VARCHAR(30)  NOT NULL,
    description VARCHAR(500),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_filiere_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE users (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    email               VARCHAR(150) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    first_name          VARCHAR(80)  NOT NULL,
    last_name           VARCHAR(80)  NOT NULL,
    role                VARCHAR(30)  NOT NULL,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    phone               VARCHAR(30),
    student_number      VARCHAR(30),
    filiere_id          BIGINT,
    specialties         VARCHAR(500),
    email_notifications BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at       DATETIME(6),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_email UNIQUE (email),
    CONSTRAINT uq_user_student_number UNIQUE (student_number),
    CONSTRAINT fk_user_filiere FOREIGN KEY (filiere_id) REFERENCES filieres (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE notifications (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT        NOT NULL,
    type         VARCHAR(40)   NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    message      VARCHAR(1000) NOT NULL,
    link         VARCHAR(255),
    is_read      BOOLEAN       NOT NULL DEFAULT FALSE,
    read_at      DATETIME(6),
    email_status VARCHAR(20)   NOT NULL DEFAULT 'NOT_REQUESTED',
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_notification_recipient_read (recipient_id, is_read)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pfe_subjects (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    title            VARCHAR(200) NOT NULL,
    description      TEXT         NOT NULL,
    keywords         VARCHAR(500),
    type             VARCHAR(20)  NOT NULL,
    company_name     VARCHAR(150),
    max_students     INT          NOT NULL DEFAULT 1,
    status           VARCHAR(20)  NOT NULL,
    rejection_reason VARCHAR(500),
    academic_year    VARCHAR(9)   NOT NULL,
    supervisor_id    BIGINT       NOT NULL,
    filiere_id       BIGINT       NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_subject_supervisor FOREIGN KEY (supervisor_id) REFERENCES users (id),
    CONSTRAINT fk_subject_filiere FOREIGN KEY (filiere_id) REFERENCES filieres (id),
    CONSTRAINT ck_subject_max_students CHECK (max_students BETWEEN 1 AND 2),
    INDEX idx_subject_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pfe_applications (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    subject_id       BIGINT      NOT NULL,
    student_id       BIGINT      NOT NULL,
    preference_rank  INT         NOT NULL,
    motivation       TEXT,
    status           VARCHAR(20) NOT NULL,
    decision_comment VARCHAR(500),
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_application_subject_student UNIQUE (subject_id, student_id),
    CONSTRAINT fk_application_subject FOREIGN KEY (subject_id) REFERENCES pfe_subjects (id) ON DELETE CASCADE,
    CONSTRAINT fk_application_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT ck_application_rank CHECK (preference_rank BETWEEN 1 AND 3)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pfe_projects (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    subject_id                BIGINT       NOT NULL,
    supervisor_id             BIGINT       NOT NULL,
    external_supervisor_name  VARCHAR(150),
    external_supervisor_email VARCHAR(150),
    status                    VARCHAR(20)  NOT NULL,
    created_at                DATETIME(6)  NOT NULL,
    updated_at                DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_project_subject UNIQUE (subject_id),
    CONSTRAINT fk_project_subject FOREIGN KEY (subject_id) REFERENCES pfe_subjects (id),
    CONSTRAINT fk_project_supervisor FOREIGN KEY (supervisor_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pfe_project_students (
    project_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    PRIMARY KEY (project_id, student_id),
    CONSTRAINT fk_project_students_project FOREIGN KEY (project_id) REFERENCES pfe_projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_students_student FOREIGN KEY (student_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pfe_milestones (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    project_id         BIGINT        NOT NULL,
    title              VARCHAR(150)  NOT NULL,
    description        VARCHAR(1000),
    due_date           DATE          NOT NULL,
    order_index        INT           NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    deliverable_url    VARCHAR(500),
    submission_comment VARCHAR(1000),
    submitted_at       DATETIME(6),
    feedback           VARCHAR(1000),
    grade              DOUBLE,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_milestone_project FOREIGN KEY (project_id) REFERENCES pfe_projects (id) ON DELETE CASCADE,
    INDEX idx_milestone_due (status, due_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pfe_defenses (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    project_id   BIGINT        NOT NULL,
    defense_date DATE          NOT NULL,
    start_time   TIME          NOT NULL,
    end_time     TIME          NOT NULL,
    room         VARCHAR(50)   NOT NULL,
    president_id BIGINT        NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    grade        DOUBLE,
    mention      VARCHAR(30),
    jury_comment VARCHAR(1000),
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_defense_project UNIQUE (project_id),
    CONSTRAINT fk_defense_project FOREIGN KEY (project_id) REFERENCES pfe_projects (id),
    CONSTRAINT fk_defense_president FOREIGN KEY (president_id) REFERENCES users (id),
    INDEX idx_defense_date (defense_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pfe_defense_jury (
    defense_id BIGINT NOT NULL,
    member_id  BIGINT NOT NULL,
    PRIMARY KEY (defense_id, member_id),
    CONSTRAINT fk_jury_defense FOREIGN KEY (defense_id) REFERENCES pfe_defenses (id) ON DELETE CASCADE,
    CONSTRAINT fk_jury_member FOREIGN KEY (member_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE internship_conventions (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    student_id          BIGINT       NOT NULL,
    project_id          BIGINT,
    company_name        VARCHAR(150) NOT NULL,
    company_address     VARCHAR(255) NOT NULL,
    company_email       VARCHAR(150),
    tutor_name          VARCHAR(150) NOT NULL,
    tutor_email         VARCHAR(150) NOT NULL,
    start_date          DATE         NOT NULL,
    end_date            DATE         NOT NULL,
    mission_description TEXT         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    review_comment      VARCHAR(500),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_convention_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_convention_project FOREIGN KEY (project_id) REFERENCES pfe_projects (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
