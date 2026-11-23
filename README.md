# SmartDept — plateforme du département informatique

Application web de gestion des **projets de fin d'études (PFE)** du département informatique
de la Faculté des Sciences d'Oujda : de la proposition du sujet jusqu'à la soutenance, avec
**notifications dans l'application et par e-mail** et un **assistant IA gratuit**.

Projet individuel — Mohammed Mrabet, Master 2026 (module Génie logiciel / DevOps).

| Couche | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring Security + JWT, Spring Data JPA, Flyway, Spring Mail, Actuator + Micrometer (Prometheus), springdoc OpenAPI |
| Base de données | MySQL 8 (H2 en mode MySQL pour les tests) |
| Frontend | Angular 22 (composants standalone, signals, lazy loading), Vitest |
| IA | API compatible OpenAI : **Ollama** (local), **Groq** ou **Google Gemini** (offres gratuites), avec repli sur un moteur local sans clé |
| DevOps | Docker multi-étapes, Docker Compose, GitHub Actions (tests, JaCoCo, Trivy, SBOM, Docker Hub), Mailpit |

---

## 1. Fonctionnalités

### Module PFE

| Acteur | Ce qu'il peut faire |
| --- | --- |
| **Enseignant** | proposer un sujet (monôme ou binôme, académique, entreprise ou recherche), l'enrichir avec l'IA, accepter ou refuser les candidatures, suivre ses projets, évaluer les livrables (validation, demande de correction, note), déclarer un projet prêt, saisir la note de soutenance s'il préside le jury |
| **Étudiant** | parcourir les sujets publiés de sa filière, candidater sur **3 sujets classés**, suivre son projet et ses jalons, rendre ses livrables (lien GitHub, Drive…), déposer sa **convention de stage**, consulter sa soutenance |
| **Chef de département** | valider ou refuser les sujets, lancer l'**affectation automatique** (par rang de préférence puis date de dépôt), programmer les soutenances avec **détection des conflits** de salle et de jury et un **jury suggéré par l'IA**, valider les conventions (version imprimable), envoyer des annonces, tableau de bord statistique |
| **Administrateur** | gérer les comptes (création, rôle, désactivation) |

Règles métier principales :

- un sujet proposé par un enseignant doit être validé par le chef ; un sujet du chef est publié directement ;
- 3 candidatures en attente au maximum, un rang de préférence ne peut être utilisé qu'une fois ;
- l'acceptation crée le projet et ses **4 jalons** (cahier des charges, conception, prototype, rapport final),
  retire les autres candidatures de l'étudiant et ferme le sujet quand il est complet ;
- un projet n'est « prêt à soutenir » que si tous ses jalons sont validés ;
- soutenance : jury de 2 enseignants minimum, président membre du jury et différent de l'encadrant,
  pas de chevauchement de salle ni de membre du jury (réponse **409** listant tous les conflits) ;
- note arrondie au quart de point, mention calculée automatiquement ;
- convention de stage de 4 à 26 semaines, motif obligatoire en cas de refus.

### Notifications (application + e-mail)

Chaque événement (sujet proposé / publié / refusé, candidature reçue / acceptée / refusée, livrable rendu,
correction demandée, projet prêt, soutenance programmée / modifiée / annulée / notée, convention soumise /
validée, annonce…) crée une notification :

- **dans l'application** : cloche avec compteur (actualisé toutes les 30 s), page Notifications, lien direct vers l'élément concerné ;
- **par e-mail** : message HTML envoyé de façon asynchrone **après la validation de la transaction**,
  avec suivi de l'état (`PENDING`, `SENT`, `FAILED`) ; chaque utilisateur peut désactiver les e-mails dans son profil ;
- **rappels automatiques** chaque matin à 8 h : jalons à rendre dans les 3 jours, soutenances du lendemain
  (sans doublon dans la journée) ; le chef peut aussi les déclencher depuis le tableau de bord.

### Assistant IA (gratuit)

| Fonction | Endpoint |
| --- | --- |
| Améliorer la description d'un sujet (objectifs, livrables, mots-clés) | `POST /api/ai/pfe/improve-subject` |
| Recommander un encadrant (spécialités, historique, charge actuelle) | `POST /api/ai/pfe/suggest-supervisors` |
| Classer les enseignants pour un jury (compétences, disponibilité sur le créneau) | `GET /api/pfe/defenses/jury-suggestions` |
| Résumer un texte | `POST /api/ai/summarize` |
| Chatbot qui connaît les données de l'utilisateur (sujets, jalons, soutenances…) | `POST /api/ai/chat` |

Sans clé ou si le fournisseur ne répond pas, l'application utilise un **moteur local** (mots-clés,
similarité, résumé extractif, intentions) : toutes les fonctions restent disponibles.

---

## 2. Lancer le projet

### Comptes de démonstration — mot de passe `Demo@2026`

| Compte | Rôle |
| --- | --- |
| `admin@smartdept.ma` | Administrateur |
| `chef@smartdept.ma` | Chef de département (Karim El Fassi) |
| `n.alami@smartdept.ma`, `y.bennani@smartdept.ma`, `s.chakir@smartdept.ma`, `h.idrissi@smartdept.ma` | Enseignants |
| `omar.tazi@etu.smartdept.ma`, `salma.berrada@etu.smartdept.ma`, … (8 étudiants) | Étudiants |

Le jeu de données (`db/demo/R__demo_data.sql`) contient 3 filières, 8 sujets, 7 candidatures, 4 projets,
10 jalons, 2 soutenances, une convention et des notifications.

### Option A — Docker (le plus simple)

```bash
cp .env.example .env          # facultatif
docker compose up -d --build
```

| URL | Service |
| --- | --- |
| http://localhost | application |
| http://localhost:8080/swagger-ui.html | documentation de l'API |
| http://localhost:8025 | **Mailpit** : tous les e-mails envoyés par l'application |
| http://localhost:8080/actuator/prometheus | métriques Prometheus |

Arrêt : `docker compose down` (ajouter `-v` pour effacer la base).

### Option B — XAMPP + Maven + Angular

1. Démarrer MySQL dans XAMPP (la base `smartdept_db` est créée automatiquement, Flyway crée les tables).
2. Backend (Git Bash) :
   ```bash
   cd backend
   ./mvnw spring-boot:run          # utilisateur root, mot de passe vide (XAMPP)
   # autre mot de passe : DB_PASSWORD=root ./mvnw spring-boot:run
   ```
3. E-mails en local (facultatif) : `docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit`.
   Sans Mailpit l'application fonctionne, les e-mails passent simplement au statut `FAILED`
   (ou lancez avec `MAIL_ENABLED=false`).
4. Frontend :
   ```bash
   cd frontend
   npm install
   npm start                       # http://localhost:4200 (proxy /api vers le port 8080)
   ```

### Activer l'IA gratuite

| Fournisseur | Variables |
| --- | --- |
| **Ollama** (local, sans compte) | `docker compose --profile ai up -d` puis `docker compose exec ollama ollama pull llama3.2:3b` ; `AI_ENABLED=true` |
| **Groq** (clé gratuite sur console.groq.com) | `AI_ENABLED=true`, `AI_BASE_URL=https://api.groq.com/openai/v1`, `AI_MODEL=llama-3.1-8b-instant`, `AI_API_KEY=gsk_…` |
| **Gemini** (clé gratuite sur aistudio.google.com) | `AI_ENABLED=true`, `AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai`, `AI_MODEL=gemini-2.0-flash`, `AI_API_KEY=AIza…` |

Ces variables se mettent dans `.env` (Docker) ou devant `./mvnw spring-boot:run`.
`GET /api/ai/status` indique le fournisseur actif.

### Envoyer de vrais e-mails (Gmail)

Créer un **mot de passe d'application** Google (compte avec validation en 2 étapes), puis :

```bash
MAIL_HOST=smtp.gmail.com MAIL_PORT=587 MAIL_AUTH=true MAIL_STARTTLS=true \
MAIL_USERNAME=votre.adresse@gmail.com MAIL_PASSWORD=motdepasseapplication \
MAIL_FROM=votre.adresse@gmail.com ./mvnw spring-boot:run
```

---

## 3. Tests et qualité

```bash
cd backend && ./mvnw test        # tests unitaires (Mockito) + intégration (H2) ; JaCoCo : target/site/jacoco/index.html
cd frontend && npm test          # Vitest
```

- **Tests unitaires** des services : règles des sujets, candidatures et affectation automatique,
  jalons, soutenances (conflits, jury, note et mention), conventions, notifications, moteur IA local.
- **Test d'intégration** `PfeWorkflowIntegrationTest` : parcours complet sur base H2
  (sujet → publication → candidature → acceptation → jalons → projet prêt → soutenance → note).
- **Frontend** : service d'authentification et utilitaires.

## 4. Intégration et déploiement continus

`.github/workflows/ci.yml`, à chaque push ou pull request sur `main` / `develop` :

1. **backend** : `./mvnw verify`, publication du rapport JaCoCo et du jar ;
2. **frontend** : `npm ci`, tests Vitest, build de production ;
3. **docker** (backend et frontend en parallèle) : build de l'image, **scan Trivy** (vulnérabilités
   HIGH / CRITICAL), **SBOM CycloneDX**, puis sur `main` **publication sur Docker Hub**
   (`latest` + SHA du commit). Secrets à créer : `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`.

### Correspondance avec les TP du module

| TP | Mise en œuvre dans SmartDept |
| --- | --- |
| TP0 Scrum / Jira | découpage en étapes livrables (voir feuille de route) |
| TP1 Git | historique local, branches `main` / `develop` |
| TP2 Tests | JUnit 5, Mockito, H2, JaCoCo, Vitest |
| TP3-1 Docker / Docker Hub | Dockerfiles multi-étapes, push automatique |
| TP3-2 Docker Compose | `docker-compose.yml` (MySQL, backend, frontend, Mailpit, Ollama) |
| TP5 Flyway | `db/migration/V1__init_schema.sql`, données `R__demo_data.sql` |
| TP7 GitHub Actions | `.github/workflows/ci.yml` |
| TP10 DevSecOps / supervision | Trivy, SBOM, Actuator + métriques Prometheus |
| TP3-2 Kubernetes, TP4 Azure, TP6 Jenkins, TP8 Terraform, TP9 Ansible, Grafana | étape 3 de la feuille de route |

## 5. Architecture

```
backend/src/main/java/ma/ump/smartdept
├── security/       JWT, filtre, configuration Spring Security
├── user/           utilisateurs, filières, authentification
├── notification/   notifications, e-mails asynchrones
├── ai/             client IA (OpenAI-compatible), moteur local, chatbot
├── pfe/            sujets, candidatures, projets, jalons, soutenances, conventions, statistiques, rappels
└── common/         entité de base, gestion uniforme des erreurs
frontend/src/app
├── core/           modèles, API, authentification, intercepteur, gardes
├── layout/         menu par rôle, cloche de notifications
├── shared/         badge, assistant IA flottant
└── pages/          tableau de bord, PFE, notifications, profil, administration
```

Diagrammes UML (PlantUML, sources et images) dans `docs/uml/` : cas d'utilisation, classes,
séquence « acceptation d'une candidature ».

Format d'erreur commun à toute l'API :

```json
{ "status": 409, "error": "Conflict", "message": "Conflit le 15/12/2026 : la salle Amphi B est occupée 09:00-10:00 …",
  "errors": null, "path": "/api/pfe/defenses", "timestamp": "2026-11-23T10:00:00" }
```

## 6. Feuille de route

- **Étape 1 (livrée)** : socle, module PFE complet, notifications application + e-mail, IA, Docker, CI.
- **Étape 2** : emploi du temps, calendrier des examens, notes et absences, planification automatique
  par l'IA, détection des étudiants en difficulté.
- **Étape 3** : Kubernetes, Jenkins, Terraform + Ansible, déploiement Azure, tableaux de bord Grafana, ELK.
