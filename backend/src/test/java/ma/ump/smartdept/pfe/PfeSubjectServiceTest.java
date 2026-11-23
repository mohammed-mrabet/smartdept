package ma.ump.smartdept.pfe;

import ma.ump.smartdept.TestData;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.pfe.PfeDtos.RejectRequest;
import ma.ump.smartdept.pfe.PfeDtos.SubjectRequest;
import ma.ump.smartdept.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static ma.ump.smartdept.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PfeSubjectServiceTest {

    @Mock
    private PfeSubjectRepository subjectRepository;
    @Mock
    private PfeApplicationRepository applicationRepository;
    @Mock
    private PfeProjectRepository projectRepository;
    @Mock
    private FiliereRepository filiereRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notifications;

    private PfeSubjectService service;
    private Filiere isi;
    private User nadia;
    private User chef;

    private static final String DESCRIPTION = "Concevoir une plateforme de suivi des stages en microservices, "
            + "avec une interface Angular et un déploiement Docker.";

    @BeforeEach
    void setUp() {
        service = new PfeSubjectService(subjectRepository, applicationRepository, projectRepository, filiereRepository,
                userRepository, notifications, TestData.CLOCK);
        isi = filiere(1, "M2I-ISI");
        nadia = teacher(3, "Nadia", "Spring Boot");
        chef = user(2, "Karim", "Chef", Role.CHEF_DEPARTEMENT);
        when(filiereRepository.findById(1L)).thenReturn(Optional.of(isi));
        when(subjectRepository.save(any(PfeSubject.class))).thenAnswer(i -> {
            PfeSubject s = i.getArgument(0);
            if (s.getId() == null) s.setId(10L);
            return s;
        });
    }

    private SubjectRequest request(SubjectType type, String company) {
        return new SubjectRequest("Suivi des stages", DESCRIPTION, "spring boot; Angular, angular , docker",
                type, company, 2, 1L, null, null);
    }

    @Test
    @DisplayName("create - enseignant : sujet PROPOSED, mots-clés nettoyés, année en cours, chef notifié")
    void create_byTeacher() {
        var r = service.create(nadia, request(SubjectType.ACADEMIQUE, null));

        assertEquals(SubjectStatus.PROPOSED, r.status());
        assertEquals(List.of("Spring boot", "Angular", "Docker"), r.keywords());
        assertEquals("2026-2027", r.academicYear());
        assertEquals("Nadia Prof", r.supervisor().fullName());
        verify(notifications).notifyManagers(eq(NotificationType.SUBJECT_PROPOSED), anyString(), anyString(), eq("/pfe/sujets/10"));
    }

    @Test
    @DisplayName("create - chef : sujet publié directement, étudiants de la filière notifiés")
    void create_byChef() {
        User omar = student(7, "Omar", isi);
        when(userRepository.findByRoleAndFiliereIdAndEnabledTrue(Role.ETUDIANT, 1L)).thenReturn(List.of(omar));

        var r = service.create(chef, request(SubjectType.ACADEMIQUE, null));

        assertEquals(SubjectStatus.PUBLISHED, r.status());
        verify(notifications).notifyAll(eq(List.of(omar)), eq(NotificationType.SUBJECT_PUBLISHED), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("create - 400 : sujet en entreprise sans nom d'entreprise ; 403 : étudiant")
    void create_rules() {
        ApiException ex = assertThrows(ApiException.class, () -> service.create(nadia, request(SubjectType.ENTREPRISE, " ")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        User omar = student(7, "Omar", isi);
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ApiException.class, () -> service.create(omar, request(SubjectType.ACADEMIQUE, null))).getStatus());
    }

    @Test
    @DisplayName("publish / reject - workflow de validation par le chef")
    void validationWorkflow() {
        PfeSubject s = subject(4, "DevSecOps", nadia, isi, SubjectStatus.PROPOSED, 1);
        when(subjectRepository.findById(4L)).thenReturn(Optional.of(s));

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.publish(nadia, 4L)).getStatus());

        var rejected = service.reject(chef, 4L, new RejectRequest("Préciser les livrables"));
        assertEquals(SubjectStatus.REJECTED, rejected.status());
        assertEquals("Préciser les livrables", rejected.rejectionReason());
        verify(notifications).notify(eq(nadia), eq(NotificationType.SUBJECT_REJECTED), anyString(), contains("Préciser"), anyString());

        var published = service.publish(chef, 4L);
        assertEquals(SubjectStatus.PUBLISHED, published.status());
        assertNull(published.rejectionReason());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.publish(chef, 4L)).getStatus());
    }

    @Test
    @DisplayName("search - un étudiant ne voit que les sujets publiés de sa filière")
    void search_student() {
        User omar = student(7, "Omar", isi);

        service.search(omar, SubjectStatus.PROPOSED, 99L, null, null, "  ", false);

        verify(subjectRepository).search(SubjectStatus.PUBLISHED, 1L, null, null, null);
    }

    @Test
    @DisplayName("canSee - sujet proposé visible par son encadrant et le chef, pas par un autre enseignant")
    void visibility() {
        PfeSubject proposed = subject(4, "X", nadia, isi, SubjectStatus.PROPOSED, 1);
        assertTrue(service.canSee(nadia, proposed));
        assertTrue(service.canSee(chef, proposed));
        assertFalse(service.canSee(teacher(4, "Youssef", ""), proposed));
        assertFalse(service.canSee(student(7, "Omar", isi), proposed));
    }

    @Test
    @DisplayName("normalizeKeywords - séparateurs , et ; doublons retirés, majuscule initiale")
    void normalizeKeywords() {
        assertEquals("Spring Boot, Angular, Docker", PfeSubjectService.normalizeKeywords("Spring Boot, angular ;; Docker,Angular"));
        assertNull(PfeSubjectService.normalizeKeywords("  "));
    }
}
