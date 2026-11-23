-- =====================================================================
-- SmartDept - V3 : scolarité (salles, modules, emploi du temps, examens, notes, absences)
-- =====================================================================

CREATE TABLE rooms (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    capacity   INT          NOT NULL,
    type       VARCHAR(10)  NOT NULL,
    building   VARCHAR(100),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_room_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE course_modules (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL,
    name        VARCHAR(150) NOT NULL,
    filiere_id  BIGINT       NOT NULL,
    semester    VARCHAR(5)   NOT NULL,
    teacher_id  BIGINT,
    coefficient DOUBLE       NOT NULL DEFAULT 1,
    cm_per_week INT          NOT NULL DEFAULT 0,
    td_per_week INT          NOT NULL DEFAULT 0,
    tp_per_week INT          NOT NULL DEFAULT 0,
    description VARCHAR(500),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_module_code UNIQUE (code),
    CONSTRAINT fk_module_filiere FOREIGN KEY (filiere_id) REFERENCES filieres (id),
    CONSTRAINT fk_module_teacher FOREIGN KEY (teacher_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE class_sessions (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    module_id        BIGINT       NOT NULL,
    teacher_id       BIGINT       NOT NULL,
    room_id          BIGINT       NOT NULL,
    filiere_id       BIGINT       NOT NULL,
    session_date     DATE         NOT NULL,
    start_time       TIME         NOT NULL,
    end_time         TIME         NOT NULL,
    session_type     VARCHAR(5)   NOT NULL,
    status           VARCHAR(15)  NOT NULL DEFAULT 'PLANNED',
    notes            VARCHAR(500),
    attendance_taken BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_session_module FOREIGN KEY (module_id) REFERENCES course_modules (id) ON DELETE CASCADE,
    CONSTRAINT fk_session_teacher FOREIGN KEY (teacher_id) REFERENCES users (id),
    CONSTRAINT fk_session_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_session_filiere FOREIGN KEY (filiere_id) REFERENCES filieres (id),
    INDEX idx_session_date (session_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE exams (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    module_id  BIGINT       NOT NULL,
    filiere_id BIGINT       NOT NULL,
    exam_date  DATE         NOT NULL,
    start_time TIME         NOT NULL,
    end_time   TIME         NOT NULL,
    exam_type  VARCHAR(15)  NOT NULL,
    notes      VARCHAR(500),
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_exam_module FOREIGN KEY (module_id) REFERENCES course_modules (id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_filiere FOREIGN KEY (filiere_id) REFERENCES filieres (id),
    INDEX idx_exam_date (exam_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE exam_rooms (
    exam_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    PRIMARY KEY (exam_id, room_id),
    CONSTRAINT fk_exam_rooms_exam FOREIGN KEY (exam_id) REFERENCES exams (id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_rooms_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE exam_supervisors (
    exam_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (exam_id, user_id),
    CONSTRAINT fk_exam_sup_exam FOREIGN KEY (exam_id) REFERENCES exams (id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_sup_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE grades (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    student_id  BIGINT       NOT NULL,
    module_id   BIGINT       NOT NULL,
    assessment  VARCHAR(15)  NOT NULL,
    grade_value DOUBLE       NOT NULL,
    published   BOOLEAN      NOT NULL DEFAULT FALSE,
    comment     VARCHAR(300),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_grade UNIQUE (student_id, module_id, assessment),
    CONSTRAINT fk_grade_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_grade_module FOREIGN KEY (module_id) REFERENCES course_modules (id) ON DELETE CASCADE,
    CONSTRAINT ck_grade_value CHECK (grade_value BETWEEN 0 AND 20)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE attendances (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    session_id           BIGINT        NOT NULL,
    student_id           BIGINT        NOT NULL,
    status               VARCHAR(10)   NOT NULL,
    justification_status VARCHAR(10)   NOT NULL DEFAULT 'NONE',
    justification        VARCHAR(1000),
    review_comment       VARCHAR(300),
    justified_at         DATETIME(6),
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_attendance UNIQUE (session_id, student_id),
    CONSTRAINT fk_attendance_session FOREIGN KEY (session_id) REFERENCES class_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
