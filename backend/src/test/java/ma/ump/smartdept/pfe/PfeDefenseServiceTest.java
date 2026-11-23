package ma.ump.smartdept.pfe;

import ma.ump.smartdept.TestData;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.pfe.PfeDtos.DefenseRequest;
import ma.ump.smartdept.pfe.PfeDtos.GradeRequest;
import ma.ump.smartdept.pfe.PfeDtos.JurySuggestion;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static ma.ump.smartdept.TestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PfeDefenseServiceTest {

    @Mock
    private PfeDefenseRepository defenseRepository;
    @Mock
    private PfeProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notifications;

    private PfeDefenseService service;

    private User chef;
    private User nadia;
    private User hanae;
    private User youssef;
    private User omar;
    private PfeProject project;

    private static final LocalDate DAY = TestData.TODAY.plusDays(10);
    private static final LocalTime NINE = LocalTime.of(9, 0);
    private static final LocalTime TEN = LocalTime.of(10, 0);

    @BeforeEach
    void setUp() {
        service = new PfeDefenseService(defenseRepository, projectRepository, userRepository, notifications, TestData.CLOCK);
        Filiere ia = filiere(2, "M2I-IA");
        chef = user(2, "Karim", "Chef", Role.CHEF_DEPARTEMENT);
        nadia = teacher(3, "Nadia", "Génie logiciel, Spring Boot, Angular");
        youssef = teacher(4, "Youssef", "Bases de données, SQL");
        hanae = teacher(6, "Hanae", "Machine learning, NLP, Python");
        omar = student(7, "Omar", ia);
        PfeSubject s = subject(5, "Chatbot NLP en Python", hanae, ia, SubjectStatus.ASSIGNED, 1);
        s.setKeywords("NLP, Python, LLM");
        project = project(1, s, omar);
        project.setStatus(ProjectStatus.READY_FOR_DEFENSE);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(defenseRepository.findByProjectId(1L)).thenReturn(Optional.empty());
        when(defenseRepository.findOverlapping(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(defenseRepository.save(any(PfeDefense.class))).thenAnswer(i -> {
            PfeDefense d = i.getArgument(0);
            if (d.getId() == null) d.setId(50L);
            return d;
        });
        for (User u : List.of(chef, nadia, youssef, hanae, omar)) {
            when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        }
    }

    private DefenseRequest request(List<Long> jury, long president, String room) {
        return new DefenseRequest(1L, DAY, NINE, TEN, room, jury, president);
    }

    private static HttpStatus status(Runnable r) {
        return assertThrows(ApiException.class, r::run).getStatus();
    }

    @Test
    @DisplayName("schedule - Succès : salle normalisée, président, étudiants et jury notifiés")
    void schedule_success() {
        var d = service.schedule(chef, request(List.of(3L, 6L), 3L, "amphi a"));

        assertEquals("AMPHI A", d.room());
        assertEquals(DefenseStatus.SCHEDULED, d.status());
        assertEquals("Nadia Prof", d.president().fullName());
        assertEquals(2, d.jury().size());
        verify(notifications, times(2)).notifyAll(anyCollection(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("schedule - Règles : projet prêt, chef uniquement, président dans le jury, encadrant non président, enseignants")
    void schedule_rules() {
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.schedule(nadia, request(List.of(3L, 6L), 3L, "A"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.schedule(chef, request(List.of(3L, 6L), 4L, "A"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.schedule(chef, request(List.of(3L, 6L), 6L, "A"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.schedule(chef, request(List.of(3L, 7L), 3L, "A"))));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.schedule(chef, request(List.of(3L, 3L), 3L, "A"))));
        project.setStatus(ProjectStatus.IN_PROGRESS);
        assertEquals(HttpStatus.CONFLICT, status(() -> service.schedule(chef, request(List.of(3L, 6L), 3L, "A"))));
    }

    @Test
    @DisplayName("schedule - 409 : salle déjà prise et membre du jury déjà occupé sur le créneau")
    void schedule_conflicts() {
        PfeSubject other = subject(9, "Autre sujet", youssef, filiere(1, "M2I-ISI"), SubjectStatus.ASSIGNED, 1);
        PfeDefense busy = new PfeDefense();
        busy.setId(60L);
        busy.setProject(project(2, other));
        busy.setDefenseDate(DAY);
        busy.setStartTime(LocalTime.of(9, 30));
        busy.setEndTime(LocalTime.of(10, 30));
        busy.setRoom("AMPHI A");
        busy.getJury().add(nadia);
        busy.setPresident(youssef);
        when(defenseRepository.findOverlapping(eq(DAY), eq(NINE), eq(TEN), isNull(), eq(DefenseStatus.SCHEDULED)))
                .thenReturn(List.of(busy));

        ApiException ex = assertThrows(ApiException.class, () -> service.schedule(chef, request(List.of(3L, 6L), 3L, "Amphi A")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("la salle AMPHI A est déjà prise"));
        assertTrue(ex.getMessage().contains("Nadia Prof siège déjà"));
        verify(defenseRepository, never()).save(any());
    }

    @Test
    @DisplayName("grade - note arrondie au quart de point, mention, projet soutenu")
    void grade() {
        PfeDefense d = new PfeDefense();
        d.setId(70L);
        d.setProject(project);
        d.setDefenseDate(TestData.TODAY);
        d.setStartTime(NINE);
        d.setEndTime(TEN);
        d.setRoom("A");
        d.setPresident(nadia);
        d.getJury().add(nadia);
        when(defenseRepository.findById(70L)).thenReturn(Optional.of(d));

        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.grade(youssef, 70L, new GradeRequest(15.0, null))));
        var r = service.grade(nadia, 70L, new GradeRequest(14.1, "Bon travail"));

        assertEquals(14.0, r.grade());
        assertEquals("Bien", r.mention());
        assertEquals(DefenseStatus.DONE, r.status());
        assertEquals(ProjectStatus.DEFENDED, project.getStatus());
    }

    @Test
    @DisplayName("mentionFor - seuils 10 / 12 / 14 / 16")
    void mentions() {
        assertEquals("Ajourné", PfeDefense.mentionFor(9.75));
        assertEquals("Passable", PfeDefense.mentionFor(10));
        assertEquals("Assez bien", PfeDefense.mentionFor(12.5));
        assertEquals("Bien", PfeDefense.mentionFor(14));
        assertEquals("Très bien", PfeDefense.mentionFor(16));
    }

    @Test
    @DisplayName("suggestJury - classement par proximité avec le sujet, membre occupé indisponible et en fin de liste")
    void suggestJury() {
        User nlp = teacher(8, "Leila", "NLP, Python, LLM");
        when(userRepository.findByRoleInAndEnabledTrue(anyList())).thenReturn(List.of(youssef, nlp, nadia, hanae));
        when(defenseRepository.findByJuryMember(anyLong())).thenReturn(List.of());
        PfeDefense busy = new PfeDefense();
        busy.getJury().add(youssef);
        when(defenseRepository.findOverlapping(eq(DAY), eq(NINE), eq(TEN), isNull(), eq(DefenseStatus.SCHEDULED)))
                .thenReturn(List.of(busy));

        List<JurySuggestion> out = service.suggestJury(chef, 1L, DAY, NINE, TEN);

        assertEquals("Leila Prof", out.get(0).teacher().fullName(), "spécialités identiques au sujet");
        assertEquals("Hanae Prof", out.get(1).teacher().fullName(), "encadrante : spécialités proches + bonus");
        assertEquals(List.of("nlp", "python", "llm"), out.get(0).matchedKeywords());
        JurySuggestion last = out.get(out.size() - 1);
        assertEquals("Youssef Prof", last.teacher().fullName());
        assertFalse(last.available());
    }
}
