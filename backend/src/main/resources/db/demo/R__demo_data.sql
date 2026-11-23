-- =====================================================================
-- SmartDept - données de démonstration (script répétable Flyway R__)
-- Mot de passe de tous les comptes : Demo@2026
-- Les dates sont relatives au jour du premier chargement (CURDATE()).
-- Production : FLYWAY_LOCATIONS=classpath:db/migration (ce dossier n'est alors pas chargé).
-- =====================================================================

INSERT INTO filieres (id, code, name, level, description, created_at, updated_at) VALUES
 (1, 'M2I-ISI', 'Master Ingénierie des Systèmes d''Information', 'MASTER', 'Génie logiciel, architectures distribuées, DevOps', NOW(), NOW()),
 (2, 'M2I-IA',  'Master Intelligence Artificielle',              'MASTER', 'Apprentissage automatique, données, NLP', NOW(), NOW()),
 (3, 'LSI',     'Licence Sciences de l''Informatique',           'LICENCE', 'Licence fondamentale', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name), level = VALUES(level), description = VALUES(description);

INSERT INTO users (id, email, password_hash, first_name, last_name, role, enabled, phone, student_number, filiere_id, specialties, email_notifications, created_at, updated_at) VALUES
 (1,  'admin@smartdept.ma',                 '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Amina',   'Admin',     'ADMIN',            TRUE, NULL, NULL,     NULL, NULL, TRUE, NOW(), NOW()),
 (2,  'chef@smartdept.ma',                  '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Karim',   'El Fassi',  'CHEF_DEPARTEMENT', TRUE, NULL, NULL,     NULL, 'Génie logiciel, Architecture logicielle, DevOps, Cloud, Gestion de projet', TRUE, NOW(), NOW()),
 (3,  'n.alami@smartdept.ma',               '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Nadia',   'Alami',     'ENSEIGNANT',       TRUE, NULL, NULL,     NULL, 'Génie logiciel, Spring Boot, Angular, Microservices, Tests logiciels', TRUE, NOW(), NOW()),
 (4,  'y.bennani@smartdept.ma',             '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Youssef', 'Bennani',   'ENSEIGNANT',       TRUE, NULL, NULL,     NULL, 'Bases de données, Big Data, Spark, Entrepôts de données, SQL, Décisionnel', TRUE, NOW(), NOW()),
 (5,  's.chakir@smartdept.ma',              '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Samir',   'Chakir',    'ENSEIGNANT',       TRUE, NULL, NULL,     NULL, 'Sécurité, Réseaux, Cybersécurité, Cloud, Kubernetes, DevSecOps, Monitoring', TRUE, NOW(), NOW()),
 (6,  'h.idrissi@smartdept.ma',             '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Hanae',   'Idrissi',   'ENSEIGNANT',       TRUE, NULL, NULL,     NULL, 'Intelligence artificielle, Machine learning, Deep learning, NLP, Python, LLM', TRUE, NOW(), NOW()),
 (7,  'omar.tazi@etu.smartdept.ma',         '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Omar',    'Tazi',      'ETUDIANT',         TRUE, NULL, 'ETU-2026-001', 1, NULL, TRUE, NOW(), NOW()),
 (8,  'salma.berrada@etu.smartdept.ma',     '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Salma',   'Berrada',   'ETUDIANT',         TRUE, NULL, 'ETU-2026-002', 1, NULL, TRUE, NOW(), NOW()),
 (9,  'mehdi.ouazzani@etu.smartdept.ma',    '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Mehdi',   'Ouazzani',  'ETUDIANT',         TRUE, NULL, 'ETU-2026-003', 2, NULL, TRUE, NOW(), NOW()),
 (10, 'imane.lahlou@etu.smartdept.ma',      '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Imane',   'Lahlou',    'ETUDIANT',         TRUE, NULL, 'ETU-2026-004', 2, NULL, TRUE, NOW(), NOW()),
 (11, 'yassine.amrani@etu.smartdept.ma',    '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Yassine', 'Amrani',    'ETUDIANT',         TRUE, NULL, 'ETU-2026-005', 1, NULL, TRUE, NOW(), NOW()),
 (12, 'rania.fikri@etu.smartdept.ma',       '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Rania',   'Fikri',     'ETUDIANT',         TRUE, NULL, 'ETU-2026-006', 1, NULL, TRUE, NOW(), NOW()),
 (13, 'ilyas.kettani@etu.smartdept.ma',     '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Ilyas',   'Kettani',   'ETUDIANT',         TRUE, NULL, 'ETU-2026-007', 2, NULL, TRUE, NOW(), NOW()),
 (14, 'hamza.bennis@etu.smartdept.ma',      '$2a$10$IqGZI6yq4bk.YeH5lV7zBeAyDvQQcQNmxZTLUKPIx9fzs2ccKsTgG', 'Hamza',   'Bennis',    'ETUDIANT',         TRUE, NULL, 'ETU-2026-008', 1, NULL, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE first_name = VALUES(first_name), last_name = VALUES(last_name), role = VALUES(role),
  filiere_id = VALUES(filiere_id), specialties = VALUES(specialties);

-- Sujets : 1 publié (binôme, 2 candidatures), 2 affecté (projet en cours), 3 publié IA, 4 proposé (à valider),
-- 5 affecté (projet prêt à soutenir), 6 entreprise publié, 7 affecté (soutenance programmée), 8 affecté (soutenu)
INSERT INTO pfe_subjects (id, title, description, keywords, type, company_name, max_students, status, rejection_reason, academic_year, supervisor_id, filiere_id, created_at, updated_at) VALUES
 (1, 'Plateforme de gestion des stages en microservices',
  'Le département gère aujourd''hui les offres et les conventions de stage par e-mail. Le projet consiste à concevoir une plateforme en microservices Spring Boot avec une interface Angular : publication des offres, candidatures, suivi par les tuteurs et tableaux de bord. Les services communiqueront via une passerelle API et seront déployés avec Docker et Kubernetes.',
  'Spring Boot, Angular, Microservices, Docker, Kubernetes', 'ACADEMIQUE', NULL, 2, 'PUBLISHED', NULL, '2026-2027', 3, 1, NOW() - INTERVAL 20 DAY, NOW()),
 (2, 'Entrepôt de données et tableaux de bord décisionnels pour l''université',
  'L''université dispose de données dispersées sur les inscriptions, les notes et les absences. Ce projet vise à construire un entrepôt de données (modèle en étoile), des flux ETL automatisés et des tableaux de bord décisionnels pour suivre la réussite des étudiants par filière et par semestre.',
  'Entrepôts de données, ETL, SQL, Décisionnel, Power BI', 'ACADEMIQUE', NULL, 1, 'ASSIGNED', NULL, '2026-2027', 4, 1, NOW() - INTERVAL 60 DAY, NOW()),
 (3, 'Détection de fraudes bancaires par apprentissage automatique',
  'Les fraudes à la carte bancaire représentent une part faible mais coûteuse des transactions. Le projet consiste à comparer plusieurs modèles de machine learning (forêts aléatoires, gradient boosting, réseaux de neurones) sur des données déséquilibrées, puis à exposer le meilleur modèle via une API Python.',
  'Machine learning, Python, Scikit-learn, Données déséquilibrées, API', 'RECHERCHE', NULL, 1, 'PUBLISHED', NULL, '2026-2027', 6, 2, NOW() - INTERVAL 15 DAY, NOW()),
 (4, 'Chaîne DevSecOps : analyse de vulnérabilités et déploiement Kubernetes',
  'Mettre en place une chaîne CI/CD sécurisée pour les applications du département : analyse statique du code, scan des images Docker avec Trivy, génération du SBOM, déploiement sur Kubernetes et supervision avec Prometheus et Grafana.',
  'DevSecOps, Trivy, Kubernetes, CI/CD, Prometheus', 'ACADEMIQUE', NULL, 1, 'PROPOSED', NULL, '2026-2027', 5, 1, NOW() - INTERVAL 2 DAY, NOW()),
 (5, 'Chatbot pédagogique basé sur un modèle de langage open source',
  'Concevoir un assistant conversationnel qui répond aux questions des étudiants à partir des supports de cours, en s''appuyant sur un modèle de langage open source exécuté localement et sur une recherche augmentée (RAG) dans une base vectorielle.',
  'LLM, NLP, RAG, Python, Ollama', 'RECHERCHE', NULL, 1, 'ASSIGNED', NULL, '2026-2027', 6, 2, NOW() - INTERVAL 150 DAY, NOW()),
 (6, 'Refonte du module de facturation de TechNova',
  'Stage de fin d''études chez TechNova : migration d''un module de facturation monolithique vers une architecture orientée services, avec reprise des données, tests automatisés et mise en production progressive.',
  'Java, Spring Boot, Migration, Tests automatisés', 'ENTREPRISE', 'TechNova', 1, 'PUBLISHED', NULL, '2026-2027', 3, 1, NOW() - INTERVAL 10 DAY, NOW()),
 (7, 'Analyse de sentiments des avis des étudiants sur les enseignements',
  'Collecter les avis anonymes des étudiants sur les enseignements et les analyser automatiquement (sentiment, thèmes récurrents) avec des modèles de NLP en français, puis restituer les résultats dans un tableau de bord.',
  'NLP, Analyse de sentiments, Python, Tableau de bord', 'RECHERCHE', NULL, 1, 'ASSIGNED', NULL, '2026-2027', 4, 2, NOW() - INTERVAL 160 DAY, NOW()),
 (8, 'Supervision d''une infrastructure cloud avec Prometheus et Grafana',
  'Déployer une pile d''observabilité pour les services du département : collecte de métriques avec Prometheus, tableaux de bord Grafana, alertes et centralisation des journaux.',
  'Monitoring, Prometheus, Grafana, Cloud, Alerting', 'ACADEMIQUE', NULL, 1, 'ASSIGNED', NULL, '2025-2026', 5, 1, NOW() - INTERVAL 300 DAY, NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title), status = VALUES(status);

INSERT INTO pfe_projects (id, subject_id, supervisor_id, external_supervisor_name, external_supervisor_email, status, created_at, updated_at) VALUES
 (1, 2, 4, NULL, NULL, 'IN_PROGRESS', NOW() - INTERVAL 45 DAY, NOW()),
 (2, 5, 6, NULL, NULL, 'READY_FOR_DEFENSE', NOW() - INTERVAL 140 DAY, NOW()),
 (3, 7, 4, NULL, NULL, 'READY_FOR_DEFENSE', NOW() - INTERVAL 150 DAY, NOW()),
 (4, 8, 5, NULL, NULL, 'DEFENDED', NOW() - INTERVAL 280 DAY, NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT IGNORE INTO pfe_project_students (project_id, student_id) VALUES (1, 7), (2, 13), (3, 10), (4, 14);

INSERT INTO pfe_applications (id, subject_id, student_id, preference_rank, motivation, status, decision_comment, created_at, updated_at) VALUES
 (1, 1, 8,  1, 'J''ai réalisé un projet Spring Boot et Angular en stage d''été et je souhaite approfondir les microservices.', 'PENDING', NULL, NOW() - INTERVAL 5 DAY, NOW()),
 (2, 1, 11, 2, 'Intéressé par Docker et Kubernetes.', 'PENDING', NULL, NOW() - INTERVAL 4 DAY, NOW()),
 (3, 3, 9,  1, 'Mon mémoire de M1 portait sur les données déséquilibrées.', 'PENDING', NULL, NOW() - INTERVAL 3 DAY, NOW()),
 (4, 6, 12, 1, 'TechNova m''a déjà proposé ce stage après un entretien.', 'PENDING', NULL, NOW() - INTERVAL 2 DAY, NOW()),
 (5, 6, 11, 1, 'Je souhaite une expérience en entreprise.', 'PENDING', NULL, NOW() - INTERVAL 1 DAY, NOW()),
 (6, 2, 7,  1, 'Passionné par la Business Intelligence.', 'ACCEPTED', 'Profil adapté', NOW() - INTERVAL 50 DAY, NOW()),
 (7, 3, 10, 2, NULL, 'WITHDRAWN', 'Étudiante affectée à un autre sujet', NOW() - INTERVAL 170 DAY, NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO pfe_milestones (id, project_id, title, description, due_date, order_index, status, deliverable_url, submission_comment, submitted_at, feedback, grade, created_at, updated_at) VALUES
 (1,  1, 'Cahier des charges', 'Contexte, besoins, périmètre et planning prévisionnel.', CURDATE() - INTERVAL 24 DAY, 1, 'VALIDATED', 'https://github.com/omar-tazi/entrepot-universite/blob/main/docs/cahier-des-charges.pdf', 'Version finale', NOW() - INTERVAL 26 DAY, 'Très bon cadrage du besoin.', 15.5, NOW(), NOW()),
 (2,  1, 'Conception', 'Modèle en étoile, flux ETL et maquettes des tableaux de bord.', CURDATE() - INTERVAL 1 DAY, 2, 'SUBMITTED', 'https://github.com/omar-tazi/entrepot-universite/tree/main/conception', 'Modèle en étoile et flux ETL', NOW() - INTERVAL 1 DAY, NULL, NULL, NOW(), NOW()),
 (3,  1, 'Rapport d''avancement et démo', 'Démonstration intermédiaire.', CURDATE() + INTERVAL 2 DAY, 3, 'TODO', NULL, NULL, NULL, NULL, NULL, NOW(), NOW()),
 (4,  1, 'Rapport final et code source', 'Rapport final, dépôt Git, présentation.', CURDATE() + INTERVAL 60 DAY, 4, 'TODO', NULL, NULL, NULL, NULL, NULL, NOW(), NOW()),
 (5,  2, 'Cahier des charges', NULL, CURDATE() - INTERVAL 120 DAY, 1, 'VALIDATED', 'https://github.com/ilyas-kettani/chatbot-pedago', NULL, NOW() - INTERVAL 121 DAY, 'Bien', 14, NOW(), NOW()),
 (6,  2, 'Conception', NULL, CURDATE() - INTERVAL 90 DAY, 2, 'VALIDATED', 'https://github.com/ilyas-kettani/chatbot-pedago', NULL, NOW() - INTERVAL 91 DAY, 'Architecture RAG claire', 15, NOW(), NOW()),
 (7,  2, 'Rapport final et code source', NULL, CURDATE() - INTERVAL 5 DAY, 3, 'VALIDATED', 'https://github.com/ilyas-kettani/chatbot-pedago', NULL, NOW() - INTERVAL 6 DAY, 'Prêt pour la soutenance', 16, NOW(), NOW()),
 (8,  3, 'Cahier des charges', NULL, CURDATE() - INTERVAL 130 DAY, 1, 'VALIDATED', 'https://github.com/imane-lahlou/avis-etudiants', NULL, NOW() - INTERVAL 131 DAY, NULL, 14, NOW(), NOW()),
 (9,  3, 'Rapport final et code source', NULL, CURDATE() - INTERVAL 8 DAY, 2, 'VALIDATED', 'https://github.com/imane-lahlou/avis-etudiants', NULL, NOW() - INTERVAL 9 DAY, NULL, 15, NOW(), NOW()),
 (10, 4, 'Rapport final et code source', NULL, CURDATE() - INTERVAL 40 DAY, 1, 'VALIDATED', 'https://github.com/hamza-bennis/observabilite', NULL, NOW() - INTERVAL 41 DAY, NULL, 16, NOW(), NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status), due_date = VALUES(due_date);

INSERT INTO pfe_defenses (id, project_id, defense_date, start_time, end_time, room, president_id, status, grade, mention, jury_comment, created_at, updated_at) VALUES
 (1, 3, CURDATE() + INTERVAL 10 DAY, '09:00:00', '10:00:00', 'AMPHI A', 6, 'SCHEDULED', NULL, NULL, NULL, NOW(), NOW()),
 (2, 4, CURDATE() - INTERVAL 20 DAY, '14:00:00', '15:00:00', 'SALLE 12', 3, 'DONE', 16.5, 'Très bien', 'Excellent travail, démonstration convaincante.', NOW(), NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT IGNORE INTO pfe_defense_jury (defense_id, member_id) VALUES (1, 6), (1, 3), (1, 4), (2, 3), (2, 5), (2, 2);

INSERT INTO internship_conventions (id, student_id, project_id, company_name, company_address, company_email, tutor_name, tutor_email, start_date, end_date, mission_description, status, review_comment, created_at, updated_at) VALUES
 (1, 12, NULL, 'TechNova', '12 boulevard Mohammed V, Oujda', 'rh@technova.ma', 'Rachid Benali', 'r.benali@technova.ma',
  CURDATE() + INTERVAL 30 DAY, CURDATE() + INTERVAL 184 DAY,
  'Participation à la refonte du module de facturation : analyse de l''existant, conception des services, développement Spring Boot, tests automatisés et mise en production.',
  'SUBMITTED', NULL, NOW() - INTERVAL 1 DAY, NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO notifications (id, recipient_id, type, title, message, link, is_read, email_status, created_at, updated_at) VALUES
 (1, 2, 'SUBJECT_PROPOSED', 'Nouveau sujet à valider', 'Samir Chakir propose le sujet « Chaîne DevSecOps : analyse de vulnérabilités et déploiement Kubernetes » (M2I-ISI).', '/pfe/sujets/4', FALSE, 'NOT_REQUESTED', NOW() - INTERVAL 2 DAY, NOW()),
 (2, 2, 'CONVENTION_SUBMITTED', 'Convention de stage à valider', 'Rania Fikri a soumis sa convention de stage chez TechNova.', '/pfe/conventions/1', FALSE, 'NOT_REQUESTED', NOW() - INTERVAL 1 DAY, NOW()),
 (3, 3, 'APPLICATION_RECEIVED', 'Nouvelle candidature', 'Salma Berrada candidate au sujet « Plateforme de gestion des stages en microservices » (choix n°1).', '/pfe/sujets/1', FALSE, 'NOT_REQUESTED', NOW() - INTERVAL 5 DAY, NOW()),
 (4, 3, 'APPLICATION_RECEIVED', 'Nouvelle candidature', 'Yassine Amrani candidate au sujet « Plateforme de gestion des stages en microservices » (choix n°2).', '/pfe/sujets/1', FALSE, 'NOT_REQUESTED', NOW() - INTERVAL 4 DAY, NOW()),
 (5, 4, 'MILESTONE_SUBMITTED', 'Livrable déposé : Conception', 'Omar Tazi a déposé « Conception » pour le projet « Entrepôt de données et tableaux de bord décisionnels pour l''université ».', '/pfe/projets/1', FALSE, 'NOT_REQUESTED', NOW() - INTERVAL 1 DAY, NOW()),
 (6, 7, 'MILESTONE_REVIEWED', 'Jalon validé : Cahier des charges', 'Votre encadrant a validé « Cahier des charges » (note : 15.5/20).', '/pfe/mon-projet', TRUE, 'NOT_REQUESTED', NOW() - INTERVAL 20 DAY, NOW()),
 (7, 10, 'DEFENSE_SCHEDULED', 'Soutenance programmée', 'Votre soutenance aura lieu dans 10 jours à 09:00, AMPHI A. Président du jury : Hanae Idrissi.', '/pfe/mon-projet', FALSE, 'NOT_REQUESTED', NOW() - INTERVAL 3 DAY, NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title);
