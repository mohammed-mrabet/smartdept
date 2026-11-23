package ma.ump.smartdept.pfe;

import ma.ump.smartdept.ai.AiDtos.ChatRequest;
import ma.ump.smartdept.ai.AiDtos.ChatResponse;
import ma.ump.smartdept.ai.AiService;
import ma.ump.smartdept.notification.NotificationRepository;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.pfe.PfeDtos.*;
import ma.ump.smartdept.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parcours complet d'un PFE sur une vraie base (H2) avec les vrais services :
 * proposition → publication → candidatures → affectation automatique → jalons → soutenance → note.
 * Vérifie aussi les requêtes JPQL et les correspondances JPA (cascades, tables d'association).
 */
@SpringBootTest
@Transactional
class PfeWorkflowIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private FiliereRepository filiereRepository;
    @Autowired private PfeSubjectService subjectService;
    @Autowired private PfeApplicationService applicationService;
    @Autowired private PfeProjectService projectService;
    @Autowired private PfeDefenseService defenseService;
    @Autowired private PfeStatsService statsService;
    @Autowired private PfeSubjectRepository subjectRepository;
    @Autowired private PfeDefenseRepository defenseRepository;
    @Autowired private PfeMilestoneRepository milestoneRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private AiService aiService;

    private Filiere isi;
    private User chef;
    private User nadia;
    private User hanae;
    private User omar;
    private User salma;

    @BeforeEach
    void setUp() {
        isi = new Filiere();
        isi.setCode("IT-ISI");
        isi.setName("Filière de test");
        isi.setLevel("MASTER");
        isi = filiereRepository.save(isi);
        chef = save("it.chef@test.ma", "Karim", Role.CHEF_DEPARTEMENT, null, "Génie logiciel");
        nadia = save("it.nadia@test.ma", "Nadia", Role.ENSEIGNANT, null, "Spring Boot, Angular, Microservices");
        hanae = save("it.hanae@test.ma", "Hanae", Role.ENSEIGNANT, null, "Machine learning, Python");
        omar = save("it.omar@test.ma", "Omar", Role.ETUDIANT, isi, null);
        salma = save("it.salma@test.ma", "Salma", Role.ETUDIANT, isi, null);
    }

    private User save(String email, String first, Role role, Filiere f, String specialties) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setFirstName(first);
        u.setLastName("Test");
        u.setRole(role);
        u.setFiliere(f);
        u.setSpecialties(specialties);
        return userRepository.save(u);
    }

    private SubjectRequest subject(String title, int max) {
        return new SubjectRequest(title, "Concevoir et développer une application web en microservices avec Spring Boot "
                + "et Angular, testée et déployée automatiquement.", "Spring Boot, Angular", SubjectType.ACADEMIQUE,
                null, max, isi.getId(), null, null);
    }

    @Test
    @DisplayName("Parcours complet d'un PFE, de la proposition du sujet à la note de soutenance")
    void fullWorkflow() {
        // 1. Proposition par l'enseignante puis publication par le chef
        SubjectResponse proposed = subjectService.create(nadia, subject("Plateforme de stages", 1));
        assertEquals(SubjectStatus.PROPOSED, proposed.status());
        assertEquals(SubjectStatus.PUBLISHED, subjectService.publish(chef, proposed.id()).status());
        SubjectResponse second = subjectService.create(chef, new SubjectRequest("Sujet du chef", "Sujet proposé par le chef "
                + "de département sur l'architecture logicielle et les tests automatisés.", null, SubjectType.RECHERCHE,
                null, 1, isi.getId(), null, null));
        assertEquals(SubjectStatus.PUBLISHED, second.status());

        // 2. Candidatures : Omar et Salma veulent tous deux le premier sujet
        applicationService.apply(omar, new ApplicationRequest(proposed.id(), 1, "Motivé"));
        applicationService.apply(salma, new ApplicationRequest(proposed.id(), 1, null));
        applicationService.apply(salma, new ApplicationRequest(second.id(), 2, null));
        assertEquals(2, subjectService.search(omar, null, null, null, null, "stages", false).size()
                + subjectService.search(omar, null, null, null, null, "chef", false).size());

        // 3. Affectation automatique : Omar (arrivé en premier) a le sujet 1, Salma son choix n°2
        AutoAssignResult result = applicationService.autoAssign(chef);
        assertEquals(2, result.assigned());
        assertEquals(SubjectStatus.ASSIGNED, subjectRepository.findById(proposed.id()).orElseThrow().getStatus());
        ProjectResponse omarProject = projectService.mine(omar);
        assertEquals("Plateforme de stages", omarProject.subjectTitle());
        assertEquals(4, omarProject.milestones().size());
        assertEquals("Sujet du chef", projectService.mine(salma).subjectTitle());

        // 4. Jalons : dépôt par l'étudiant, validation par l'encadrante
        for (MilestoneResponse m : omarProject.milestones()) {
            projectService.submitMilestone(omar, m.id(), new SubmitMilestoneRequest("https://github.com/omar/stages", null));
            projectService.reviewMilestone(nadia, m.id(), new ReviewMilestoneRequest(MilestoneStatus.VALIDATED, "OK", 15.0));
        }
        ProjectResponse ready = projectService.markReady(nadia, omarProject.id());
        assertEquals(ProjectStatus.READY_FOR_DEFENSE, ready.status());
        assertEquals(100, ready.progress());

        // 5. Soutenance aujourd'hui, jury suggéré puis note
        LocalDate today = LocalDate.now();
        List<JurySuggestion> suggestions = defenseService.suggestJury(chef, omarProject.id(), today, LocalTime.of(9, 0), LocalTime.of(10, 0));
        assertFalse(suggestions.isEmpty());
        DefenseResponse defense = defenseService.schedule(chef, new DefenseRequest(omarProject.id(), today,
                LocalTime.of(9, 0), LocalTime.of(10, 0), "Salle 1", List.of(hanae.getId(), nadia.getId()), hanae.getId()));
        assertEquals("SALLE 1", defense.room());
        assertEquals(1, defenseRepository.findOverlapping(today, LocalTime.of(9, 30), LocalTime.of(11, 0), null, DefenseStatus.SCHEDULED).size());
        assertEquals(1, defenseRepository.findByJuryMember(nadia.getId()).size());

        DefenseResponse graded = defenseService.grade(hanae, defense.id(), new GradeRequest(16.5, "Très bon travail"));
        assertEquals("Très bien", graded.mention());
        assertEquals(ProjectStatus.DEFENDED, projectService.mine(omar).status());

        // 6. Indicateurs, notifications et chatbot (moteur local)
        PfeStats stats = statsService.stats();
        assertEquals(1L, stats.defensesDone());
        assertTrue(notificationRepository.countByRecipientIdAndReadFalse(omar.getId()) >= 5);
        assertTrue(notificationService.list(omar, true, 50).size() >= 5);
        assertTrue(notificationService.markAllRead(omar) >= 5);
        assertEquals(0, notificationService.unreadCount(omar));
        ChatResponse chat = aiService.chat(omar, new ChatRequest("Quelle est ma note de soutenance ?"));
        assertEquals("local", chat.source());
        assertTrue(chat.answer().contains("16.5"), chat.answer());
    }

    @Test
    @DisplayName("Requêtes : recherche de sujets, jalons à échéance, utilisateur avec sa filière")
    void queries() {
        SubjectResponse s = subjectService.create(nadia, subject("Recherche de sujets", 2));
        assertEquals(1, subjectRepository.search(SubjectStatus.PROPOSED, isi.getId(), nadia.getId(), SubjectType.ACADEMIQUE, "recherche").size());
        assertEquals(1, subjectRepository.search(null, null, null, null, "angular").size());
        assertTrue(subjectRepository.search(SubjectStatus.PUBLISHED, null, null, null, null).isEmpty());
        assertEquals(s.id(), subjectRepository.findBySupervisorId(nadia.getId()).get(0).getId());

        assertTrue(milestoneRepository.findByStatusInAndDueDateBetween(List.of(MilestoneStatus.TODO),
                LocalDate.now(), LocalDate.now().plusDays(3)).isEmpty());
        User loaded = userRepository.findWithFiliere(omar.getId()).orElseThrow();
        assertEquals("IT-ISI", loaded.getFiliere().getCode());
        assertEquals(1, userRepository.search(Role.ETUDIANT, "omar").size());
    }
}
