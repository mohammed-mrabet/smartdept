package ma.ump.smartdept.academic;

import ma.ump.smartdept.academic.AcademicDtos.*;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.common.StudyLevel;
import ma.ump.smartdept.notification.NotificationRepository;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scolarité sur une vraie base H2 : référentiel, emploi du temps (conflits 409, planification automatique),
 * examens, appel et avertissement d'absences, notes et relevé, suivi des étudiants.
 */
@SpringBootTest
@Transactional
class AcademicIntegrationTest {

    @Autowired private FiliereRepository filiereRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReferenceService references;
    @Autowired private TimetableService timetable;
    @Autowired private ExamService exams;
    @Autowired private GradeService grades;
    @Autowired private AttendanceService attendance;
    @Autowired private RiskService risks;
    @Autowired private NotificationRepository notificationRepository;

    private Filiere isi;
    private User chef;
    private User nadia;
    private User samir;
    private User omar;
    private User hamza;
    private RoomResponse amphi;
    private RoomResponse s12;
    private RoomResponse labo;
    private ModuleResponse gl;
    private ModuleResponse dev;
    private LocalDate nextMonday;

    @BeforeEach
    void setUp() {
        isi = new Filiere();
        isi.setCode("AC-ISI");
        isi.setName("Filière scolarité");
        isi.setLevel("MASTER");
        isi.setStudyLevel(StudyLevel.BAC_5);
        isi = filiereRepository.save(isi);
        chef = save("ac.chef@test.ma", "Karim", Role.CHEF_DEPARTEMENT, null);
        nadia = save("ac.nadia@test.ma", "Nadia", Role.ENSEIGNANT, null);
        samir = save("ac.samir@test.ma", "Samir", Role.ENSEIGNANT, null);
        omar = save("ac.omar@test.ma", "Omar", Role.ETUDIANT, isi);
        hamza = save("ac.hamza@test.ma", "Hamza", Role.ETUDIANT, isi);
        amphi = references.createRoom(new RoomRequest("AC-AMPHI", "Amphi", 100, RoomType.AMPHI, "A", true));
        s12 = references.createRoom(new RoomRequest("AC-S12", "Salle 12", 30, RoomType.SALLE, "B", true));
        labo = references.createRoom(new RoomRequest("AC-LAB", "Labo", 20, RoomType.LABO, "C", true));
        gl = references.createModule(new ModuleRequest("AC-GL", "Génie logiciel", isi.getId(), "S3", nadia.getId(), 2, 1, 1, 1, null));
        dev = references.createModule(new ModuleRequest("AC-DEV", "DevOps", isi.getId(), "S3", samir.getId(), 1, 1, 0, 1, null));
        nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private User save(String email, String first, Role role, Filiere f) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setFirstName(first);
        u.setLastName("Test");
        u.setRole(role);
        u.setFiliere(f);
        u.setStudentNumber(role == Role.ETUDIANT ? email : null);
        return userRepository.save(u);
    }

    private SessionRequest session(ModuleResponse m, RoomResponse room, LocalDate date, String start, String end, SessionType type, Integer weeks) {
        return new SessionRequest(m.id(), null, room.id(), date, LocalTime.parse(start), LocalTime.parse(end), type, null, weeks);
    }

    @Test
    @DisplayName("séance en conflit (salle, enseignant, filière) → 409 qui liste chaque conflit")
    void conflictsAreDetected() {
        timetable.create(session(gl, amphi, nextMonday, "08:30", "10:30", SessionType.CM, null));
        ApiException e = assertThrows(ApiException.class,
                () -> timetable.create(session(gl, amphi, nextMonday, "09:00", "11:00", SessionType.CM, null)));
        assertEquals(409, e.getStatus().value());
        assertTrue(e.getMessage().contains("Nadia Test a déjà cours"));
        assertTrue(e.getMessage().contains("la salle AC-AMPHI est occupée"));
        assertTrue(e.getMessage().contains("la filière AC-ISI a déjà cours"));
        // créneau contigu : autorisé
        assertEquals(1, timetable.create(session(dev, amphi, nextMonday, "10:30", "12:30", SessionType.CM, null)).size());
        // salle trop petite
        references.createRoom(new RoomRequest("AC-MINI", "Mini", 1, RoomType.SALLE, null, true));
        ApiException small = assertThrows(ApiException.class, () -> timetable.create(new SessionRequest(dev.id(), null,
                references.rooms().stream().filter(r -> r.code().equals("AC-MINI")).findFirst().orElseThrow().id(),
                nextMonday.plusDays(1), LocalTime.of(8, 30), LocalTime.of(10, 30), SessionType.TD, null, null)));
        assertEquals(400, small.getStatus().value());
    }

    @Test
    @DisplayName("série hebdomadaire, semaine de la filière, annulation notifiée")
    void seriesWeekAndCancel() {
        List<SessionResponse> created = timetable.create(session(gl, s12, nextMonday.plusDays(2), "14:00", "16:00", SessionType.TD, 3));
        assertEquals(3, created.size());
        assertEquals(nextMonday.plusDays(16), created.get(2).date());
        assertEquals(1, timetable.week(nextMonday.plusDays(7), isi.getId(), null, null).size());
        assertEquals(1, timetable.mine(omar, nextMonday).size(), "l'étudiant voit sa filière");
        assertEquals(1, timetable.mine(nadia, nextMonday).size(), "l'enseignante voit ses cours");
        SessionResponse cancelled = timetable.cancel(created.get(0).id(), "Enseignante en jury");
        assertEquals(SessionStatus.CANCELLED, cancelled.status());
        assertTrue(notificationRepository.findAll().stream().anyMatch(n -> n.getType() == NotificationType.SESSION_CHANGED
                && n.getRecipient().getId().equals(omar.getId())));
        // une séance annulée libère le créneau
        assertEquals(1, timetable.create(session(dev, s12, nextMonday.plusDays(2), "14:00", "16:00", SessionType.CM, null)).size());
    }

    @Test
    @DisplayName("planification automatique : proposition puis enregistrement sur 2 semaines")
    void automaticPlanning() {
        PlanPreview p = timetable.preview(new PlanPreviewRequest(isi.getId(), nextMonday, "S3"));
        assertEquals(5, p.required());
        assertEquals(5, p.slots().size(), p.unplaced().toString());
        assertTrue(p.slots().stream().filter(s -> s.type() == SessionType.TP).allMatch(s -> s.roomCode().equals("AC-LAB")));
        List<SlotInput> inputs = p.slots().stream().map(s -> new SlotInput(s.moduleId(), s.teacherId(), s.roomId(), s.date(),
                s.startTime(), s.endTime(), s.type())).toList();
        PlanApplyResult r = timetable.apply(new PlanApplyRequest(isi.getId(), 2, inputs));
        assertEquals(10, r.created());
        assertEquals(0, r.skipped());
        // rejouer la même proposition : tout est désormais en conflit
        assertEquals(10, timetable.apply(new PlanApplyRequest(isi.getId(), 2, inputs)).skipped());
    }

    @Test
    @DisplayName("examens : capacité, conflit avec un cours, planification automatique avec surveillants")
    void exams() {
        timetable.create(session(gl, amphi, nextMonday, "08:30", "10:30", SessionType.CM, null));
        ApiException busy = assertThrows(ApiException.class, () -> exams.create(new ExamRequest(dev.id(), nextMonday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), ExamType.FINAL, List.of(s12.id()), List.of(samir.getId()), null)));
        assertTrue(busy.getMessage().contains("la filière a cours"));
        ExamResponse ok = exams.create(new ExamRequest(dev.id(), nextMonday.plusDays(1), LocalTime.of(9, 0), LocalTime.of(11, 0),
                ExamType.FINAL, List.of(s12.id()), List.of(samir.getId()), null));
        assertEquals(30, ok.capacity());
        assertEquals(2, ok.students());
        assertEquals(1, exams.mine(omar).size());

        ExamPlanPreview plan = exams.preview(new ExamPlanRequest(isi.getId(), nextMonday.plusDays(7), nextMonday.plusDays(12), "S3", ExamType.FINAL, 120));
        assertEquals(1, plan.slots().size(), "DEV a déjà son examen final : seul GL est planifié");
        assertEquals("AC-GL", plan.slots().get(0).moduleCode());
        assertFalse(plan.slots().get(0).supervisorIds().isEmpty());
        List<ExamResponse> created = exams.applyPlan(new ExamPlanApplyRequest(isi.getId(), ExamType.FINAL, plan.slots()));
        assertEquals(1, created.size());
    }

    @Test
    @DisplayName("appel, avertissement au 3e absence, justificatif accepté par le chef")
    void attendanceFlow() {
        LocalDate past = LocalDate.now().minusDays(1);
        List<Long> ids = timetable.create(session(dev, s12, past.minusWeeks(2), "14:00", "16:00", SessionType.CM, 3))
                .stream().map(SessionResponse::id).toList();
        for (Long id : ids) {
            attendance.save(samir, id, new AttendanceBatchRequest(List.of(
                    new AttendanceEntry(omar.getId(), AttendanceStatus.PRESENT), new AttendanceEntry(hamza.getId(), AttendanceStatus.ABSENT))));
        }
        assertTrue(notificationRepository.findAll().stream().anyMatch(n -> n.getType() == NotificationType.ABSENCE_WARNING
                && n.getRecipient().getId().equals(hamza.getId())), "avertissement à la 3e absence");
        assertThrows(ApiException.class, () -> attendance.save(nadia, ids.get(0), new AttendanceBatchRequest(List.of())),
                "seul l'enseignant de la séance fait l'appel");

        List<AbsenceResponse> mine = attendance.mine(hamza);
        assertEquals(3, mine.size());
        AbsenceResponse j = attendance.justify(hamza, mine.get(0).id(), new JustifyRequest("Certificat médical"));
        assertEquals(JustificationStatus.PENDING, j.justificationStatus());
        assertEquals(1, attendance.pending(chef).size());
        assertEquals(JustificationStatus.ACCEPTED, attendance.review(chef, j.id(), new ReviewRequest(true, null)).justificationStatus());
    }

    @Test
    @DisplayName("notes : saisie, publication, relevé avec compensation ; suivi des étudiants")
    void gradesTranscriptAndRisk() {
        grades.save(nadia, gl.id(), new GradeBatchRequest(Assessment.CC, List.of(new GradeEntry(omar.getId(), 14.0, null), new GradeEntry(hamza.getId(), 5.0, null))));
        grades.save(nadia, gl.id(), new GradeBatchRequest(Assessment.EXAM, List.of(new GradeEntry(omar.getId(), 12.0, null), new GradeEntry(hamza.getId(), 6.0, null))));
        grades.save(samir, dev.id(), new GradeBatchRequest(Assessment.EXAM, List.of(new GradeEntry(omar.getId(), 8.0, null), new GradeEntry(hamza.getId(), 4.0, null))));
        assertThrows(ApiException.class, () -> grades.save(samir, gl.id(), new GradeBatchRequest(Assessment.CC, List.of())),
                "un enseignant ne note que ses modules");

        assertNull(grades.transcript(omar, omar.getId()).overallAverage(), "rien n'est publié");
        grades.publish(nadia, gl.id(), Assessment.CC);
        grades.publish(nadia, gl.id(), Assessment.EXAM);
        grades.publish(samir, dev.id(), Assessment.EXAM);
        assertTrue(notificationRepository.findAll().stream().anyMatch(n -> n.getType() == NotificationType.GRADES_PUBLISHED
                && n.getRecipient().getId().equals(omar.getId())));

        Transcript t = grades.transcript(omar, omar.getId());
        TranscriptLine glLine = t.semesters().get(0).lines().stream().filter(l -> l.code().equals("AC-GL")).findFirst().orElseThrow();
        TranscriptLine devLine = t.semesters().get(0).lines().stream().filter(l -> l.code().equals("AC-DEV")).findFirst().orElseThrow();
        assertEquals(12.8, glLine.average());           // 0,4 × 14 + 0,6 × 12
        assertEquals("VALIDE", glLine.status());
        assertEquals("COMPENSE", devLine.status(), "8/20 compensé par la moyenne du semestre (11,2)");
        assertEquals(11.2, t.overallAverage());
        assertThrows(ApiException.class, () -> grades.transcript(omar, hamza.getId()), "un étudiant ne voit pas le relevé d'un autre");

        List<RiskResponse> list = risks.assess(isi.getId());
        assertEquals(hamza.getId(), list.get(0).student().id(), "Hamza est en tête du suivi");
        assertEquals(RiskLevel.HIGH, list.get(0).level());
        assertTrue(risks.explain(hamza.getId()).text().contains("tutorat"));
    }
}
