package ma.ump.smartdept.pfe;

import ma.ump.smartdept.TestData;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.pfe.PfeDtos.ApplicationRequest;
import ma.ump.smartdept.pfe.PfeDtos.AutoAssignResult;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ma.ump.smartdept.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PfeApplicationServiceTest {

    @Mock
    private PfeApplicationRepository applicationRepository;
    @Mock
    private PfeSubjectRepository subjectRepository;
    @Mock
    private PfeProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notifications;

    private PfeApplicationService service;

    private Filiere isi;
    private User nadia;
    private User omar;
    private User salma;
    private PfeSubject binome;

    @BeforeEach
    void setUp() {
        service = new PfeApplicationService(applicationRepository, subjectRepository, projectRepository,
                userRepository, notifications, TestData.CLOCK);
        isi = filiere(1, "M2I-ISI");
        nadia = teacher(3, "Nadia", "Spring Boot");
        omar = student(7, "Omar", isi);
        salma = student(8, "Salma", isi);
        binome = subject(1, "Plateforme de stages", nadia, isi, SubjectStatus.PUBLISHED, 2);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(binome));
        when(applicationRepository.save(any(PfeApplication.class))).thenAnswer(i -> i.getArgument(0));
        when(projectRepository.save(any(PfeProject.class))).thenAnswer(i -> {
            PfeProject p = i.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });
        when(applicationRepository.findByStudentIdAndStatus(anyLong(), any())).thenReturn(List.of());
        when(applicationRepository.findBySubjectIdAndStatus(anyLong(), any())).thenReturn(List.of());
    }

    private static HttpStatus status(Runnable r) {
        return assertThrows(ApiException.class, r::run).getStatus();
    }

    // ---------- candidature ----------

    @Test
    @DisplayName("apply - Succès : candidature en attente, l'encadrant est notifié")
    void apply_success() {
        var response = service.apply(omar, new ApplicationRequest(1L, 1, "Motivé"));

        assertEquals(ApplicationStatus.PENDING, response.status());
        assertEquals(1, response.preferenceRank());
        verify(notifications).notify(eq(nadia), eq(NotificationType.APPLICATION_RECEIVED), anyString(), contains("Omar"), anyString());
    }

    @Test
    @DisplayName("apply - Règles : rôle, sujet publié, filière, déjà affecté, doublon, 3 maximum, rang unique")
    void apply_rules() {
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.apply(nadia, new ApplicationRequest(1L, 1, null))));

        binome.setStatus(SubjectStatus.PROPOSED);
        assertEquals(HttpStatus.CONFLICT, status(() -> service.apply(omar, new ApplicationRequest(1L, 1, null))));
        binome.setStatus(SubjectStatus.PUBLISHED);

        User otherFiliere = student(9, "Mehdi", filiere(2, "M2I-IA"));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.apply(otherFiliere, new ApplicationRequest(1L, 1, null))));

        when(projectRepository.existsByStudentId(7L)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, status(() -> service.apply(omar, new ApplicationRequest(1L, 1, null))));
        when(projectRepository.existsByStudentId(7L)).thenReturn(false);

        when(applicationRepository.existsBySubjectIdAndStudentId(1L, 7L)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, status(() -> service.apply(omar, new ApplicationRequest(1L, 1, null))));
        when(applicationRepository.existsBySubjectIdAndStudentId(1L, 7L)).thenReturn(false);

        PfeSubject s2 = subject(2, "B", nadia, isi, SubjectStatus.PUBLISHED, 1);
        PfeSubject s3 = subject(3, "C", nadia, isi, SubjectStatus.PUBLISHED, 1);
        PfeSubject s4 = subject(4, "D", nadia, isi, SubjectStatus.PUBLISHED, 1);
        when(applicationRepository.findByStudentIdAndStatus(7L, ApplicationStatus.PENDING))
                .thenReturn(List.of(application(11, s2, omar, 1), application(12, s3, omar, 2), application(13, s4, omar, 3)));
        ApiException full = assertThrows(ApiException.class, () -> service.apply(omar, new ApplicationRequest(1L, 1, null)));
        assertTrue(full.getMessage().contains("3 candidatures"));

        when(applicationRepository.findByStudentIdAndStatus(7L, ApplicationStatus.PENDING))
                .thenReturn(List.of(application(11, s2, omar, 1)));
        ApiException rank = assertThrows(ApiException.class, () -> service.apply(omar, new ApplicationRequest(1L, 1, null)));
        assertTrue(rank.getMessage().contains("choix n°1"));
        verify(applicationRepository, never()).save(any());
    }

    // ---------- acceptation ----------

    @Test
    @DisplayName("accept - premier étudiant : projet créé avec 4 jalons, autres candidatures de l'étudiant retirées")
    void accept_createsProject() {
        PfeApplication a = application(20, binome, omar, 1);
        PfeSubject other = subject(5, "Autre", nadia, isi, SubjectStatus.PUBLISHED, 1);
        PfeApplication otherApp = application(21, other, omar, 2);
        otherApp.setStatus(ApplicationStatus.PENDING);
        when(applicationRepository.findById(20L)).thenReturn(Optional.of(a));
        when(projectRepository.findBySubjectId(1L)).thenReturn(Optional.empty());
        when(applicationRepository.findByStudentIdAndStatus(7L, ApplicationStatus.PENDING)).thenReturn(List.of(otherApp));

        service.accept(nadia, 20L, null);

        assertEquals(ApplicationStatus.ACCEPTED, a.getStatus());
        assertEquals(ApplicationStatus.WITHDRAWN, otherApp.getStatus());
        assertEquals(SubjectStatus.PUBLISHED, binome.getStatus(), "binôme : une place reste libre");
        ArgumentCaptor<PfeProject> captor = ArgumentCaptor.forClass(PfeProject.class);
        verify(projectRepository, atLeastOnce()).save(captor.capture());
        PfeProject project = captor.getValue();
        assertTrue(project.hasMember(omar));
        assertEquals(4, project.getMilestones().size());
        assertEquals(TestData.TODAY.plusWeeks(3), project.getMilestones().get(0).getDueDate());
        verify(notifications).notify(eq(omar), eq(NotificationType.APPLICATION_ACCEPTED), anyString(), anyString(), eq("/pfe/mon-projet"));
        verify(notifications).notify(eq(nadia), eq(NotificationType.PROJECT_CREATED), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("accept - deuxième étudiant : sujet complet (ASSIGNED), candidatures restantes refusées")
    void accept_fillsSubject() {
        PfeProject existing = project(100, binome, omar);
        PfeApplication a = application(22, binome, salma, 1);
        User yassine = student(11, "Yassine", isi);
        PfeApplication remaining = application(23, binome, yassine, 2);
        when(applicationRepository.findById(22L)).thenReturn(Optional.of(a));
        when(projectRepository.findBySubjectId(1L)).thenReturn(Optional.of(existing));
        when(applicationRepository.findBySubjectIdAndStatus(1L, ApplicationStatus.PENDING)).thenReturn(List.of(remaining));

        service.accept(nadia, 22L, null);

        assertEquals(2, existing.getStudents().size());
        assertEquals(SubjectStatus.ASSIGNED, binome.getStatus());
        assertEquals(ApplicationStatus.REJECTED, remaining.getStatus());
        assertEquals("Sujet complet", remaining.getDecisionComment());
        verify(notifications).notify(eq(yassine), eq(NotificationType.APPLICATION_REJECTED), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("accept - refus : sujet déjà complet, ou utilisateur ni encadrant ni chef")
    void accept_refused() {
        PfeSubject solo = subject(6, "Solo", nadia, isi, SubjectStatus.PUBLISHED, 1);
        PfeApplication a = application(24, solo, salma, 1);
        when(applicationRepository.findById(24L)).thenReturn(Optional.of(a));
        when(projectRepository.findBySubjectId(6L)).thenReturn(Optional.of(project(101, solo, omar)));

        assertEquals(HttpStatus.CONFLICT, status(() -> service.accept(nadia, 24L, null)));
        User otherTeacher = teacher(4, "Youssef", "SQL");
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.accept(otherTeacher, 24L, null)));
    }

    // ---------- affectation automatique ----------

    @Test
    @DisplayName("autoAssign - rang 1 servi d'abord ; un étudiant affecté n'est pas affecté deux fois")
    void autoAssign() {
        User chef = user(2, "Karim", "Chef", Role.CHEF_DEPARTEMENT);
        PfeSubject s1 = subject(1, "S1", nadia, isi, SubjectStatus.PUBLISHED, 1);
        PfeSubject s2 = subject(2, "S2", nadia, isi, SubjectStatus.PUBLISHED, 1);
        PfeApplication omarChoice1 = application(31, s1, omar, 1);
        PfeApplication salmaChoice1 = application(32, s1, salma, 1);   // arrivée après Omar : sujet déjà pris
        PfeApplication omarChoice2 = application(33, s2, omar, 2);
        PfeApplication salmaChoice2 = application(34, s2, salma, 2);
        List<PfeApplication> ordered = List.of(omarChoice1, salmaChoice1, omarChoice2, salmaChoice2);
        when(applicationRepository.findByStatusAndSubjectStatusInOrderByPreferenceRankAscCreatedAtAscIdAsc(any(), anyCollection()))
                .thenReturn(ordered);
        List<Long> assigned = new ArrayList<>();
        when(projectRepository.existsByStudentId(anyLong())).thenAnswer(i -> assigned.contains(i.<Long>getArgument(0)));
        when(projectRepository.findBySubjectId(anyLong())).thenReturn(Optional.empty());
        when(projectRepository.save(any(PfeProject.class))).thenAnswer(i -> {
            PfeProject p = i.getArgument(0);
            p.getStudents().forEach(s -> assigned.add(s.getId()));
            return p;
        });
        when(applicationRepository.findBySubjectIdAndStatus(1L, ApplicationStatus.PENDING)).thenReturn(List.of(salmaChoice1));
        when(applicationRepository.findByStudentIdAndStatus(7L, ApplicationStatus.PENDING)).thenReturn(List.of(omarChoice2));
        when(userRepository.findByRoleAndEnabledTrueOrderByLastNameAsc(Role.ETUDIANT)).thenReturn(List.of(omar, salma));

        AutoAssignResult result = service.autoAssign(chef);

        assertEquals(2, result.assigned());
        assertEquals(ApplicationStatus.ACCEPTED, omarChoice1.getStatus());
        assertEquals(ApplicationStatus.REJECTED, salmaChoice1.getStatus());
        assertEquals(ApplicationStatus.WITHDRAWN, omarChoice2.getStatus());
        assertEquals(ApplicationStatus.ACCEPTED, salmaChoice2.getStatus());
        assertTrue(result.details().get(0).contains("Omar"));
        assertTrue(result.studentsWithoutSubject().isEmpty());
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.autoAssign(nadia)));
    }
}
