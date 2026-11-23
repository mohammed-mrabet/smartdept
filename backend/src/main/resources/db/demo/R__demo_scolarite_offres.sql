-- =====================================================================
-- SmartDept - données de démonstration des étapes 2 et 3 : scolarité et offres de stage (script répétable)
-- Exécuté après R__demo_data.sql. Les séances sont placées dans la semaine du premier chargement.
-- Les entreprises des offres sont fictives : les vraies offres arrivent par la synchronisation des API.
-- =====================================================================

SET @monday = DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY);

-- ---------------------------------------------------------------- niveaux d'études et nouvelles filières
UPDATE filieres SET study_level = 'BAC_5' WHERE id IN (1, 2);
UPDATE filieres SET study_level = 'BAC_3' WHERE id = 3;
INSERT INTO filieres (id, code, name, level, study_level, description, created_at, updated_at) VALUES
 (4, 'M1-GL',     'Master 1 Génie Logiciel',                          'MASTER', 'BAC_4', 'Conception objet, tests, systèmes distribués', NOW(), NOW()),
 (5, 'DEUST-MIP', 'DEUST Mathématiques, Informatique et Physique',    'DEUST',  'BAC_2', 'Bases de l''algorithmique et de la programmation', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), study_level = VALUES(study_level), description = VALUES(description);

-- ---------------------------------------------------------------- étudiants supplémentaires et compétences (utilisées par l'IA des offres)
INSERT INTO users (id, email, password_hash, first_name, last_name, role, enabled, student_number, filiere_id, specialties, email_notifications, created_at, updated_at) VALUES
 (15, 'nora.alaoui@etu.smartdept.ma', '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Nora', 'Alaoui', 'ETUDIANT', TRUE, 'ETU-2026-009', 3, 'HTML, CSS, JavaScript, PHP, MySQL', TRUE, NOW(), NOW()),
 (16, 'adam.cherkaoui@etu.smartdept.ma', '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Adam', 'Cherkaoui', 'ETUDIANT', TRUE, 'ETU-2026-010', 3, 'Java, Python, SQL, Linux', TRUE, NOW(), NOW()),
 (17, 'lina.sefrioui@etu.smartdept.ma', '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Lina', 'Sefrioui', 'ETUDIANT', TRUE, 'ETU-2026-011', 4, 'Java, Spring Boot, React, Git, Tests', TRUE, NOW(), NOW()),
 (18, 'karim.zahraoui@etu.smartdept.ma', '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Karim', 'Zahraoui', 'ETUDIANT', TRUE, 'ETU-2026-012', 4, 'C#, .NET, SQL, Azure', TRUE, NOW(), NOW()),
 (19, 'sara.benjelloun@etu.smartdept.ma', '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Sara', 'Benjelloun', 'ETUDIANT', TRUE, 'ETU-2026-013', 5, 'Excel, Python, Réseaux', TRUE, NOW(), NOW()),
 (20, 'youness.raji@etu.smartdept.ma', '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Youness', 'Raji', 'ETUDIANT', TRUE, 'ETU-2026-014', 5, 'C++, Linux, Arduino', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE first_name = VALUES(first_name), last_name = VALUES(last_name), filiere_id = VALUES(filiere_id), specialties = VALUES(specialties);

UPDATE users SET specialties = 'Java, Spring Boot, Angular, Docker, SQL, Git' WHERE id = 7;
UPDATE users SET specialties = 'Java, Spring, Microservices, Kubernetes, Scrum' WHERE id = 8;
UPDATE users SET specialties = 'Python, Machine learning, Deep learning, NLP, Data' WHERE id = 9;
UPDATE users SET specialties = 'Python, Data, Power BI, SQL, Spark' WHERE id = 10;
UPDATE users SET specialties = 'Angular, TypeScript, Node.js, MongoDB' WHERE id = 11;
UPDATE users SET specialties = 'Java, Spring Boot, SQL, Tests, Selenium' WHERE id = 12;
UPDATE users SET specialties = 'Python, LLM, NLP, Docker, Cloud' WHERE id = 13;
UPDATE users SET specialties = 'PHP, Laravel, MySQL' WHERE id = 14;

-- ---------------------------------------------------------------- salles
INSERT INTO rooms (id, code, name, capacity, type, building, active, created_at, updated_at) VALUES
 (1, 'AMPHI-A', 'Amphithéâtre A', 120, 'AMPHI', 'Bloc A', TRUE, NOW(), NOW()),
 (2, 'AMPHI-B', 'Amphithéâtre B', 80,  'AMPHI', 'Bloc A', TRUE, NOW(), NOW()),
 (3, 'S12',     'Salle 12',       40,  'SALLE', 'Bloc B', TRUE, NOW(), NOW()),
 (4, 'S14',     'Salle 14',       35,  'SALLE', 'Bloc B', TRUE, NOW(), NOW()),
 (5, 'S21',     'Salle 21',       30,  'SALLE', 'Bloc C', TRUE, NOW(), NOW()),
 (6, 'LABO-1',  'Laboratoire informatique 1', 24, 'LABO', 'Bloc C', TRUE, NOW(), NOW()),
 (7, 'LABO-2',  'Laboratoire informatique 2', 20, 'LABO', 'Bloc C', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), capacity = VALUES(capacity), type = VALUES(type), building = VALUES(building);

-- ---------------------------------------------------------------- modules (volume hebdomadaire en séances de 2 h)
INSERT INTO course_modules (id, code, name, filiere_id, semester, teacher_id, coefficient, cm_per_week, td_per_week, tp_per_week, description, created_at, updated_at) VALUES
 (1, 'GL501', 'Architecture logicielle avancée', 1, 'S3', 3, 2, 1, 1, 1, 'Microservices, DDD, Spring Boot, Angular', NOW(), NOW()),
 (2, 'DEV502', 'DevOps et Cloud', 1, 'S3', 5, 2, 1, 0, 1, 'Docker, Kubernetes, Jenkins, Terraform, Ansible', NOW(), NOW()),
 (3, 'BD503', 'Big Data et entrepôts de données', 1, 'S3', 4, 1.5, 1, 1, 0, 'Spark, Hadoop, entrepôts, SQL avancé', NOW(), NOW()),
 (4, 'IA504', 'Apprentissage automatique', 1, 'S3', 6, 1.5, 1, 0, 1, 'Machine learning supervisé, scikit-learn', NOW(), NOW()),
 (5, 'MGT505', 'Gestion de projet agile', 1, 'S3', 2, 1, 1, 0, 0, 'Scrum, Jira, estimation', NOW(), NOW()),
 (6, 'IA601', 'Deep learning', 2, 'S3', 6, 2, 1, 0, 1, 'Réseaux de neurones, PyTorch', NOW(), NOW()),
 (7, 'NLP602', 'Traitement automatique des langues', 2, 'S3', 6, 2, 1, 1, 0, 'NLP, LLM, RAG', NOW(), NOW()),
 (8, 'DATA603', 'Ingénierie des données', 2, 'S3', 4, 1.5, 1, 0, 1, 'Pipelines, Spark, Airflow', NOW(), NOW()),
 (9, 'MLO604', 'MLOps', 2, 'S3', 5, 1, 1, 0, 1, 'Déploiement de modèles, monitoring', NOW(), NOW()),
 (10, 'LSI501', 'Programmation web', 3, 'S5', 3, 2, 1, 1, 1, 'HTML, CSS, JavaScript, PHP', NOW(), NOW()),
 (11, 'LSI502', 'Réseaux informatiques', 3, 'S5', 5, 1.5, 1, 1, 0, 'TCP/IP, routage, sécurité', NOW(), NOW()),
 (12, 'LSI503', 'Bases de données relationnelles', 3, 'S5', 4, 1.5, 1, 1, 1, 'SQL, normalisation, transactions', NOW(), NOW()),
 (13, 'GL401', 'Conception orientée objet', 4, 'S1', 3, 2, 1, 1, 0, 'UML, design patterns', NOW(), NOW()),
 (14, 'GL402', 'Tests et qualité logicielle', 4, 'S1', 3, 1.5, 1, 0, 1, 'JUnit, Mockito, Selenium, SonarQube', NOW(), NOW()),
 (15, 'GL403', 'Systèmes distribués', 4, 'S1', 5, 1.5, 1, 1, 0, 'Concurrence, RPC, messagerie', NOW(), NOW()),
 (16, 'MIP301', 'Algorithmique et structures de données', 5, 'S3', 4, 2, 1, 1, 1, 'Tri, listes, arbres', NOW(), NOW()),
 (17, 'MIP302', 'Programmation en C', 5, 'S3', 2, 2, 1, 0, 1, 'Pointeurs, mémoire, fichiers', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), teacher_id = VALUES(teacher_id), coefficient = VALUES(coefficient), cm_per_week = VALUES(cm_per_week), td_per_week = VALUES(td_per_week), tp_per_week = VALUES(tp_per_week);

-- ---------------------------------------------------------------- emploi du temps : semaine précédente (appel fait) et semaine en cours
INSERT INTO class_sessions (id, module_id, teacher_id, room_id, filiere_id, session_date, start_time, end_time, session_type, status, attendance_taken, created_at, updated_at) VALUES
 (1, 1, 3, 1, 1, DATE_ADD(@monday, INTERVAL -7 DAY), '08:30', '10:30', 'CM', 'PLANNED', TRUE, NOW(), NOW()),
 (2, 1, 3, 6, 1, DATE_ADD(@monday, INTERVAL -5 DAY), '14:00', '16:00', 'TP', 'PLANNED', TRUE, NOW(), NOW()),
 (3, 2, 5, 1, 1, DATE_ADD(@monday, INTERVAL -6 DAY), '08:30', '10:30', 'CM', 'PLANNED', TRUE, NOW(), NOW()),
 (4, 2, 5, 6, 1, DATE_ADD(@monday, INTERVAL -4 DAY), '10:45', '12:45', 'TP', 'PLANNED', TRUE, NOW(), NOW()),
 (5, 3, 4, 3, 1, DATE_ADD(@monday, INTERVAL -7 DAY), '10:45', '12:45', 'CM', 'PLANNED', TRUE, NOW(), NOW()),
 (6, 3, 4, 3, 1, DATE_ADD(@monday, INTERVAL -4 DAY), '14:00', '16:00', 'TD', 'PLANNED', TRUE, NOW(), NOW()),
 (7, 4, 6, 1, 1, DATE_ADD(@monday, INTERVAL -5 DAY), '08:30', '10:30', 'CM', 'PLANNED', TRUE, NOW(), NOW()),
 (8, 4, 6, 7, 1, DATE_ADD(@monday, INTERVAL -3 DAY), '10:45', '12:45', 'TP', 'PLANNED', TRUE, NOW(), NOW()),
 (9, 5, 2, 2, 1, DATE_ADD(@monday, INTERVAL -3 DAY), '14:00', '16:00', 'CM', 'PLANNED', TRUE, NOW(), NOW()),
 (10, 1, 3, 4, 1, DATE_ADD(@monday, INTERVAL -6 DAY), '14:00', '16:00', 'TD', 'PLANNED', TRUE, NOW(), NOW()),
 (11, 6, 6, 2, 2, DATE_ADD(@monday, INTERVAL -7 DAY), '14:00', '16:00', 'CM', 'PLANNED', TRUE, NOW(), NOW()),
 (12, 7, 6, 2, 2, DATE_ADD(@monday, INTERVAL -6 DAY), '14:00', '16:00', 'CM', 'PLANNED', TRUE, NOW(), NOW()),
 (13, 8, 4, 7, 2, DATE_ADD(@monday, INTERVAL -5 DAY), '10:45', '12:45', 'TP', 'PLANNED', TRUE, NOW(), NOW()),
 (14, 9, 5, 6, 2, DATE_ADD(@monday, INTERVAL -3 DAY), '08:30', '10:30', 'TP', 'PLANNED', TRUE, NOW(), NOW()),
 (15, 10, 3, 2, 3, DATE_ADD(@monday, INTERVAL -6 DAY), '10:45', '12:45', 'CM', 'PLANNED', TRUE, NOW(), NOW()),
 (16, 11, 5, 3, 3, DATE_ADD(@monday, INTERVAL -5 DAY), '14:00', '16:00', 'CM', 'PLANNED', TRUE, NOW(), NOW()),
 (17, 12, 4, 6, 3, DATE_ADD(@monday, INTERVAL -7 DAY), '16:15', '18:15', 'TP', 'PLANNED', TRUE, NOW(), NOW()),
 (18, 1, 3, 1, 1, DATE_ADD(@monday, INTERVAL 0 DAY), '08:30', '10:30', 'CM', 'PLANNED', FALSE, NOW(), NOW()),
 (19, 1, 3, 6, 1, DATE_ADD(@monday, INTERVAL 2 DAY), '14:00', '16:00', 'TP', 'PLANNED', FALSE, NOW(), NOW()),
 (20, 2, 5, 1, 1, DATE_ADD(@monday, INTERVAL 1 DAY), '08:30', '10:30', 'CM', 'PLANNED', FALSE, NOW(), NOW()),
 (21, 2, 5, 6, 1, DATE_ADD(@monday, INTERVAL 3 DAY), '10:45', '12:45', 'TP', 'PLANNED', FALSE, NOW(), NOW()),
 (22, 3, 4, 3, 1, DATE_ADD(@monday, INTERVAL 0 DAY), '10:45', '12:45', 'CM', 'PLANNED', FALSE, NOW(), NOW()),
 (23, 3, 4, 3, 1, DATE_ADD(@monday, INTERVAL 3 DAY), '14:00', '16:00', 'TD', 'PLANNED', FALSE, NOW(), NOW()),
 (24, 4, 6, 1, 1, DATE_ADD(@monday, INTERVAL 2 DAY), '08:30', '10:30', 'CM', 'PLANNED', FALSE, NOW(), NOW()),
 (25, 4, 6, 7, 1, DATE_ADD(@monday, INTERVAL 4 DAY), '10:45', '12:45', 'TP', 'PLANNED', FALSE, NOW(), NOW()),
 (26, 5, 2, 2, 1, DATE_ADD(@monday, INTERVAL 4 DAY), '14:00', '16:00', 'CM', 'PLANNED', FALSE, NOW(), NOW()),
 (27, 1, 3, 4, 1, DATE_ADD(@monday, INTERVAL 1 DAY), '14:00', '16:00', 'TD', 'PLANNED', FALSE, NOW(), NOW()),
 (28, 6, 6, 2, 2, DATE_ADD(@monday, INTERVAL 0 DAY), '14:00', '16:00', 'CM', 'PLANNED', FALSE, NOW(), NOW()),
 (29, 7, 6, 2, 2, DATE_ADD(@monday, INTERVAL 1 DAY), '14:00', '16:00', 'CM', 'PLANNED', FALSE, NOW(), NOW()),
 (30, 8, 4, 7, 2, DATE_ADD(@monday, INTERVAL 2 DAY), '10:45', '12:45', 'TP', 'PLANNED', FALSE, NOW(), NOW()),
 (31, 9, 5, 6, 2, DATE_ADD(@monday, INTERVAL 4 DAY), '08:30', '10:30', 'TP', 'PLANNED', FALSE, NOW(), NOW()),
 (32, 10, 3, 2, 3, DATE_ADD(@monday, INTERVAL 1 DAY), '10:45', '12:45', 'CM', 'PLANNED', FALSE, NOW(), NOW()),
 (33, 11, 5, 3, 3, DATE_ADD(@monday, INTERVAL 2 DAY), '14:00', '16:00', 'CM', 'PLANNED', FALSE, NOW(), NOW()),
 (34, 12, 4, 6, 3, DATE_ADD(@monday, INTERVAL 0 DAY), '16:15', '18:15', 'TP', 'PLANNED', FALSE, NOW(), NOW())
ON DUPLICATE KEY UPDATE session_date = VALUES(session_date), start_time = VALUES(start_time), end_time = VALUES(end_time), room_id = VALUES(room_id), status = 'PLANNED';

-- ---------------------------------------------------------------- appel de la semaine précédente (M2I-ISI)
INSERT INTO attendances (id, session_id, student_id, status, justification_status, justification, justified_at, created_at, updated_at) VALUES
 (1, 1, 7, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (2, 1, 8, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (3, 1, 11, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (4, 1, 12, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (5, 1, 14, 'ABSENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (6, 2, 7, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (7, 2, 8, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (8, 2, 11, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (9, 2, 12, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (10, 2, 14, 'ABSENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (11, 3, 7, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (12, 3, 8, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (13, 3, 11, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (14, 3, 12, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (15, 3, 14, 'ABSENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (16, 4, 7, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (17, 4, 8, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (18, 4, 11, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (19, 4, 12, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (20, 4, 14, 'ABSENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (21, 5, 7, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (22, 5, 8, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (23, 5, 11, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (24, 5, 12, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (25, 5, 14, 'ABSENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (26, 6, 7, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (27, 6, 8, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (28, 6, 11, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (29, 6, 12, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (30, 6, 14, 'ABSENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (31, 7, 7, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (32, 7, 8, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (33, 7, 11, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (34, 7, 12, 'ABSENT', 'PENDING', 'Rendez-vous médical (certificat déposé au secrétariat)', NOW() - INTERVAL 2 DAY, NOW(), NOW()),
 (35, 7, 14, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (36, 8, 7, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (37, 8, 8, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (38, 8, 11, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (39, 8, 12, 'ABSENT', 'PENDING', 'Rendez-vous médical (certificat déposé au secrétariat)', NOW() - INTERVAL 2 DAY, NOW(), NOW()),
 (40, 8, 14, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (41, 9, 7, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (42, 9, 8, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (43, 9, 11, 'LATE', 'NONE', NULL, NULL, NOW(), NOW()),
 (44, 9, 12, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (45, 9, 14, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (46, 10, 7, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (47, 10, 8, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (48, 10, 11, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (49, 10, 12, 'PRESENT', 'NONE', NULL, NULL, NOW(), NOW()),
 (50, 10, 14, 'ABSENT', 'NONE', NULL, NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status), justification_status = VALUES(justification_status);

-- ---------------------------------------------------------------- examens (dans 3 et 4 semaines)
INSERT INTO exams (id, module_id, filiere_id, exam_date, start_time, end_time, exam_type, notes, created_at, updated_at) VALUES
 (1, 1, 1, DATE_ADD(@monday, INTERVAL 22 DAY), '09:00', '11:00', 'FINAL', 'Documents non autorisés', NOW(), NOW()),
 (2, 3, 1, DATE_ADD(@monday, INTERVAL 24 DAY), '09:00', '11:00', 'FINAL', NULL, NOW(), NOW()),
 (3, 6, 2, DATE_ADD(@monday, INTERVAL 23 DAY), '14:00', '16:00', 'FINAL', NULL, NOW(), NOW()),
 (4, 10, 3, DATE_ADD(@monday, INTERVAL 29 DAY), '09:00', '11:00', 'FINAL', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE exam_date = VALUES(exam_date), start_time = VALUES(start_time), end_time = VALUES(end_time);
INSERT IGNORE INTO exam_rooms (exam_id, room_id) VALUES (1, 3), (2, 3), (3, 4), (4, 2);
INSERT IGNORE INTO exam_supervisors (exam_id, user_id) VALUES (1, 3), (1, 5), (2, 4), (2, 6), (3, 6), (3, 4), (4, 3), (4, 5);

-- ---------------------------------------------------------------- notes publiées (M2I-ISI)
INSERT INTO grades (id, student_id, module_id, assessment, grade_value, published, created_at, updated_at) VALUES
 (1, 7, 1, 'CC', 15, TRUE, NOW(), NOW()),
 (2, 7, 1, 'EXAM', 14.5, TRUE, NOW(), NOW()),
 (3, 7, 1, 'TP', 16, TRUE, NOW(), NOW()),
 (4, 7, 3, 'CC', 13, TRUE, NOW(), NOW()),
 (5, 7, 3, 'EXAM', 12.5, TRUE, NOW(), NOW()),
 (6, 7, 2, 'CC', 15.5, TRUE, NOW(), NOW()),
 (7, 7, 4, 'CC', 14, TRUE, NOW(), NOW()),
 (8, 8, 1, 'CC', 14, TRUE, NOW(), NOW()),
 (9, 8, 1, 'EXAM', 13, TRUE, NOW(), NOW()),
 (10, 8, 1, 'TP', 15, TRUE, NOW(), NOW()),
 (11, 8, 3, 'CC', 12, TRUE, NOW(), NOW()),
 (12, 8, 3, 'EXAM', 13.5, TRUE, NOW(), NOW()),
 (13, 8, 2, 'CC', 16, TRUE, NOW(), NOW()),
 (14, 8, 4, 'CC', 12.5, TRUE, NOW(), NOW()),
 (15, 11, 1, 'CC', 11, TRUE, NOW(), NOW()),
 (16, 11, 1, 'EXAM', 10.5, TRUE, NOW(), NOW()),
 (17, 11, 1, 'TP', 12, TRUE, NOW(), NOW()),
 (18, 11, 3, 'CC', 10, TRUE, NOW(), NOW()),
 (19, 11, 3, 'EXAM', 9.5, TRUE, NOW(), NOW()),
 (20, 11, 2, 'CC', 12, TRUE, NOW(), NOW()),
 (21, 11, 4, 'CC', 10.5, TRUE, NOW(), NOW()),
 (22, 12, 1, 'CC', 12.5, TRUE, NOW(), NOW()),
 (23, 12, 1, 'EXAM', 11, TRUE, NOW(), NOW()),
 (24, 12, 1, 'TP', 13, TRUE, NOW(), NOW()),
 (25, 12, 3, 'CC', 11, TRUE, NOW(), NOW()),
 (26, 12, 3, 'EXAM', 10, TRUE, NOW(), NOW()),
 (27, 12, 2, 'CC', 13, TRUE, NOW(), NOW()),
 (28, 12, 4, 'CC', 11.5, TRUE, NOW(), NOW()),
 (29, 14, 1, 'CC', 7, TRUE, NOW(), NOW()),
 (30, 14, 1, 'EXAM', 6, TRUE, NOW(), NOW()),
 (31, 14, 1, 'TP', 8.5, TRUE, NOW(), NOW()),
 (32, 14, 3, 'CC', 8, TRUE, NOW(), NOW()),
 (33, 14, 3, 'EXAM', 7.5, TRUE, NOW(), NOW()),
 (34, 14, 2, 'CC', 6.5, TRUE, NOW(), NOW()),
 (35, 14, 4, 'CC', 8, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE grade_value = VALUES(grade_value), published = VALUES(published);

-- ---------------------------------------------------------------- offres du département (entreprises fictives)
INSERT INTO internship_offers (id, source, external_id, title, company, location, country, kind, level_min, level_max, remote, description, url, salary, skills, duration_months, published_at, contact_email, posted_by_id, active, fetched_at, created_at, updated_at) VALUES
 (1, 'INTERNAL', 'demo-1', 'Stage PFE — Développeur Full Stack Spring Boot / Angular', 'Atlas Numérique (entreprise fictive)', 'Casablanca, Maroc', 'MA', 'PFE', 5, 5, FALSE, 'Stage de fin détudes de 6 mois au sein dune équipe produit. Missions : conception de microservices Spring Boot, interfaces Angular, tests automatisés et intégration continue avec GitHub Actions et Docker. Profil : Bac+5 (Master ou cycle ingénieur) en informatique.', NULL, 'Gratification 4 000 MAD/mois', 'Java, Spring, Angular, Docker, Tests', 6, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'rh@atlas-numerique.exemple.ma', 2, TRUE, NOW(), NOW(), NOW()),
 (2, 'INTERNAL', 'demo-2', 'Stage PFE Data Engineer — pipelines Spark et Airflow', 'Maghreb Data Lab (entreprise fictive)', 'Rabat, Maroc', 'MA', 'PFE', 5, 5, TRUE, 'Construction de pipelines de données (Spark, Airflow), modélisation dun entrepôt et tableaux de bord Power BI. Télétravail partiel possible. Niveau Master 2.', NULL, NULL, 'Python, Spark, SQL, Power BI, Data', 6, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 'stages@maghreb-datalab.exemple.ma', 2, TRUE, NOW(), NOW(), NOW()),
 (3, 'INTERNAL', 'demo-3', 'Stage Développeur Web PHP / Laravel (Bac+3)', 'Oriental Web Services (entreprise fictive)', 'Oujda, Maroc', 'MA', 'STAGE', 3, 3, FALSE, 'Stage de 3 mois pour licence : développement de modules Laravel, intégration HTML/CSS et base MySQL.', NULL, 'Indemnité 2 000 MAD/mois', 'PHP, Laravel, MySQL, JavaScript', 3, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'contact@orientalweb.exemple.ma', 2, TRUE, NOW(), NOW(), NOW()),
 (4, 'INTERNAL', 'demo-4', 'Stage technicien support informatique (DEUST, BTS, DUT)', 'Clinique Al Amal (établissement fictif)', 'Oujda, Maroc', 'MA', 'STAGE', 2, 2, FALSE, 'Stage de 2 mois : support utilisateurs, installation de postes, réseau local, inventaire. Ouvert aux Bac+2.', NULL, NULL, 'Réseaux, Linux, Excel', 2, DATE_SUB(CURDATE(), INTERVAL 6 DAY), 'it@alamal.exemple.ma', 2, TRUE, NOW(), NOW(), NOW()),
 (5, 'INTERNAL', 'demo-5', 'Stage Master 1 — Tests automatisés et qualité', 'QualiSoft Tanger (entreprise fictive)', 'Tanger, Maroc', 'MA', 'STAGE', 4, 4, FALSE, 'Stage de 4 mois pour étudiant en Master 1 : automatisation de tests (JUnit, Selenium), intégration SonarQube dans Jenkins.', NULL, NULL, 'Java, Tests, Selenium, Jenkins', 4, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 'jobs@qualisoft.exemple.ma', 2, TRUE, NOW(), NOW(), NOW()),
 (6, 'INTERNAL', 'demo-6', 'Stage de fin détudes — Ingénieur DevOps Kubernetes', 'NordCloud Lyon (entreprise fictive)', 'Lyon, France', 'FR', 'PFE', 5, 5, TRUE, 'Stage de fin détudes (6 mois) : industrialisation de déploiements Kubernetes avec Terraform et Ansible, supervision Prometheus/Grafana. Bac+5. Gratification légale.', NULL, 'Gratification légale', 'Kubernetes, Terraform, Ansible, Docker, Cloud', 6, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 'talents@nordcloud.exemple.fr', 2, TRUE, NOW(), NOW(), NOW()),
 (7, 'INTERNAL', 'demo-7', 'Alternance Développeur Java (Bac+3 à Bac+5)', 'Banque Rhône Digital (entreprise fictive)', 'Paris, France', 'FR', 'ALTERNANCE', 3, 5, FALSE, 'Contrat dapprentissage de 12 à 24 mois. Développement dAPI REST Java/Spring, méthode Scrum.', NULL, NULL, 'Java, Spring, API REST, Scrum', 12, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 'alternance@rhone-digital.exemple.fr', 2, TRUE, NOW(), NOW(), NOW()),
 (8, 'INTERNAL', 'demo-8', 'Stage Data Scientist — NLP et LLM', 'Lexia Labs (entreprise fictive)', 'Toulouse, France', 'FR', 'PFE', 5, 5, TRUE, 'Stage de 6 mois : fine-tuning de modèles de langue, RAG, évaluation. Master 2 IA ou école dingénieur.', NULL, NULL, 'Python, NLP, LLM, Deep learning', 6, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'jobs@lexialabs.exemple.fr', 2, TRUE, NOW(), NOW(), NOW()),
 (9, 'INTERNAL', 'demo-9', 'Stage assistant développeur (BTS / BUT 2e année)', 'Mairie numérique de Lille (structure fictive)', 'Lille, France', 'FR', 'STAGE', 2, 2, FALSE, 'Stage de 8 semaines pour Bac+2 : maintenance dun site web et petits développements JavaScript.', NULL, NULL, 'JavaScript, PHP', 2, DATE_SUB(CURDATE(), INTERVAL 8 DAY), 'stages@lille-numerique.exemple.fr', 2, TRUE, NOW(), NOW(), NOW()),
 (10, 'INTERNAL', 'demo-10', 'Stage développeur mobile Flutter (Licence)', 'AppLab Bordeaux (entreprise fictive)', 'Bordeaux, France', 'FR', 'STAGE', 3, 3, FALSE, 'Stage de 3 mois : application mobile Flutter connectée à Firebase. Niveau licence ou bachelor.', NULL, NULL, 'Flutter, Android, Git', 3, DATE_SUB(CURDATE(), INTERVAL 6 DAY), 'jobs@applab.exemple.fr', 2, TRUE, NOW(), NOW(), NOW()),
 (11, 'INTERNAL', 'demo-11', 'Stage en génie logiciel — Montréal (Bac+4/Bac+5)', 'Érable Logiciels (entreprise fictive)', 'Montréal, Québec, Canada', 'CA', 'STAGE', 4, 5, FALSE, 'Stage rémunéré de 4 mois (session dhiver) : développement .NET et React, tests unitaires. Permis de stage requis.', NULL, '22 CAD/h', 'C#, .NET, React, Tests', 4, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'stages@erable.exemple.ca', 2, TRUE, NOW(), NOW(), NOW()),
 (12, 'INTERNAL', 'demo-12', 'Co-op Software Developer Intern — Toronto', 'Maple Fintech (fictional company)', 'Toronto, Ontario, Canada', 'CA', 'STAGE', 3, 5, TRUE, '4-month co-op internship for bachelor or master students: Java microservices, AWS, CI/CD. Hybrid work.', NULL, '25 CAD/hour', 'Java, AWS, Microservices, Docker', 4, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 'careers@maplefintech.example.ca', 2, TRUE, NOW(), NOW(), NOW()),
 (13, 'INTERNAL', 'demo-13', 'Stage PFE — Intelligence artificielle appliquée à la santé', 'Laval Santé Numérique (fictif)', 'Laval, Québec, Canada', 'CA', 'PFE', 5, 5, FALSE, 'Projet de fin détudes de 6 mois : modèles de classification dimages médicales avec PyTorch. Master 2.', NULL, NULL, 'Python, Deep learning, Machine learning', 6, DATE_SUB(CURDATE(), INTERVAL 9 DAY), 'recherche@laval-sante.exemple.ca', 2, TRUE, NOW(), NOW(), NOW()),
 (14, 'INTERNAL', 'demo-14', 'Stage Développeur Angular — Bruxelles', 'Atomium Software (entreprise fictive)', 'Bruxelles, Belgique', 'BE', 'STAGE', 3, 5, FALSE, 'Stage de 3 à 6 mois : front-end Angular et TypeScript, collaboration avec une équipe Spring Boot.', NULL, NULL, 'Angular, TypeScript, Spring', 4, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 'jobs@atomium-software.exemple.be', 2, TRUE, NOW(), NOW(), NOW()),
 (15, 'INTERNAL', 'demo-15', 'Stage PFE Cybersécurité — SOC et analyse de vulnérabilités', 'Meuse Security (entreprise fictive)', 'Liège, Belgique', 'BE', 'PFE', 5, 5, FALSE, 'Stage de fin détudes : mise en place dun SOC, analyse Trivy/SBOM, durcissement Linux. Bac+5.', NULL, NULL, 'Cybersécurité, Linux, Docker', 6, DATE_SUB(CURDATE(), INTERVAL 7 DAY), 'security@meuse.exemple.be', 2, TRUE, NOW(), NOW(), NOW()),
 (16, 'INTERNAL', 'demo-16', 'Stage technicien réseaux (Bac+2)', 'Gand Telecom (entreprise fictive)', 'Gand, Belgique', 'BE', 'STAGE', 2, 3, FALSE, 'Stage de 10 semaines : câblage, configuration de commutateurs, supervision réseau.', NULL, NULL, 'Réseaux, Linux', 2, DATE_SUB(CURDATE(), INTERVAL 11 DAY), 'hr@gandtelecom.exemple.be', 2, TRUE, NOW(), NOW(), NOW()),
 (17, 'INTERNAL', 'demo-17', 'Prácticas Desarrollador Backend Java (Grado o Máster)', 'Iberia Code (empresa ficticia)', 'Madrid, España', 'ES', 'STAGE', 3, 5, TRUE, 'Prácticas de 6 meses: desarrollo backend Java/Spring, bases de datos PostgreSQL, teletrabajo parcial.', NULL, '800 €/mes', 'Java, Spring, PostgreSQL', 6, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'empleo@iberiacode.example.es', 2, TRUE, NOW(), NOW(), NOW()),
 (18, 'INTERNAL', 'demo-18', 'Prácticas fin de máster — Data Analytics', 'Barcelona Insights (empresa ficticia)', 'Barcelona, España', 'ES', 'PFE', 5, 5, FALSE, 'Trabajo de fin de máster en empresa: análisis de datos con Python y Power BI.', NULL, NULL, 'Python, Power BI, SQL, Data', 5, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 'talento@bcn-insights.example.es', 2, TRUE, NOW(), NOW(), NOW()),
 (19, 'INTERNAL', 'demo-19', 'Prácticas Técnico Superior en Sistemas (FP II / Bac+2)', 'Valencia Sistemas (empresa ficticia)', 'Valencia, España', 'ES', 'STAGE', 2, 2, FALSE, 'Prácticas de 3 meses para técnico superior: soporte, redes y virtualización.', NULL, NULL, 'Réseaux, Linux', 3, DATE_SUB(CURDATE(), INTERVAL 10 DAY), 'rrhh@valenciasistemas.example.es', 2, TRUE, NOW(), NOW(), NOW()),
 (20, 'INTERNAL', 'demo-20', 'Remote Internship — Frontend React (Europe)', 'Remote Studio (fictional company)', 'Remote - Europe', 'REMOTE', 'STAGE', 3, 5, TRUE, 'Fully remote 3-month internship for bachelor or master students. React, TypeScript, Figma.', NULL, NULL, 'React, TypeScript, Figma', 3, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'jobs@remotestudio.example.com', 2, TRUE, NOW(), NOW(), NOW()),
 (21, 'INTERNAL', 'demo-21', 'Junior DevOps Engineer — premier emploi (Bac+5)', 'CloudOps Remote (fictional company)', 'Remote - Worldwide', 'REMOTE', 'EMPLOI', 5, 5, TRUE, 'Premier emploi en télétravail pour jeunes diplômés Bac+5 : Kubernetes, Terraform, AWS.', NULL, '40-50 k€/an', 'Kubernetes, Terraform, AWS, Linux', NULL, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 'careers@cloudops.example.com', 2, TRUE, NOW(), NOW(), NOW()),
 (22, 'INTERNAL', 'demo-22', 'Stage PFE — Plateforme e-learning en microservices', 'Département informatique FSO (projet interne)', 'Oujda, Maroc', 'MA', 'PFE', 5, 5, FALSE, 'Projet interne proposé par le département avec une entreprise partenaire : plateforme e-learning (Spring Boot, Angular, Keycloak).', NULL, NULL, 'Java, Spring, Angular, Microservices', 6, DATE_SUB(CURDATE(), INTERVAL 0 DAY), 'chef@smartdept.ma', 2, TRUE, NOW(), NOW(), NOW()),
 (23, 'INTERNAL', 'demo-23', 'Stage dété développeur (toutes années)', 'Startup Fès Innov (entreprise fictive)', 'Fès, Maroc', 'MA', 'STAGE', NULL, NULL, FALSE, 'Stage dété de 2 mois ouvert à tous les niveaux : développement web et automatisation.', NULL, NULL, 'JavaScript, Python', 2, DATE_SUB(CURDATE(), INTERVAL 12 DAY), 'contact@fesinnov.exemple.ma', 2, TRUE, NOW(), NOW(), NOW()),
 (24, 'INTERNAL', 'demo-24', 'Alternance BTS SIO / DUT informatique', 'Hauts-de-Seine Digital (structure fictive)', 'Nanterre, France', 'FR', 'ALTERNANCE', 2, 2, FALSE, 'Alternance de 24 mois pour préparer un Bac+2 : support et développement doutils internes.', NULL, NULL, 'Réseaux, PHP, SQL', 24, DATE_SUB(CURDATE(), INTERVAL 7 DAY), 'apprentissage@hds-digital.exemple.fr', 2, TRUE, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title), description = VALUES(description), published_at = VALUES(published_at), active = TRUE;

-- ---------------------------------------------------------------- suivi et alertes d'Omar et de Mehdi
INSERT INTO offer_bookmarks (id, user_id, offer_id, status, notes, applied_at, follow_up_sent, created_at, updated_at) VALUES
 (1, 7, 1, 'APPLIED', 'CV + lettre envoyés par e-mail', DATE_SUB(CURDATE(), INTERVAL 16 DAY), FALSE, NOW(), NOW()),
 (2, 7, 6, 'INTERVIEW', 'Entretien technique jeudi 10 h (visio)', DATE_SUB(CURDATE(), INTERVAL 9 DAY), FALSE, NOW(), NOW()),
 (3, 7, 14, 'SAVED', NULL, NULL, FALSE, NOW(), NOW()),
 (4, 7, 22, 'SAVED', 'Demander des précisions au chef de département', NULL, FALSE, NOW(), NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status), notes = VALUES(notes);
INSERT INTO offer_alerts (id, user_id, name, keywords, country, kind, study_level, remote_only, active, matches_sent, created_at, updated_at) VALUES
 (1, 7, 'PFE Spring Boot au Maroc', 'spring', 'MA', 'PFE', 5, FALSE, TRUE, 0, NOW(), NOW()),
 (2, 9, 'Stages IA en télétravail', 'python', NULL, NULL, 5, TRUE, TRUE, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

