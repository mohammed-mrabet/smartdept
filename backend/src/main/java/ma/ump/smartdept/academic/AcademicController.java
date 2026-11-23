package ma.ump.smartdept.academic;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.academic.AcademicDtos.*;
import ma.ump.smartdept.security.CurrentUser;
import ma.ump.smartdept.user.User;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Étape 2 : salles, modules, emploi du temps, examens, notes, absences, suivi des étudiants. */
@RestController
@RequestMapping("/api/academic")
@Tag(name = "Scolarité", description = "Emploi du temps, examens, notes, absences et suivi des étudiants")
public class AcademicController {

    private static final String MANAGERS = "hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')";
    private static final String STAFF = "hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')";

    private final ReferenceService references;
    private final TimetableService timetable;
    private final ExamService exams;
    private final GradeService grades;
    private final AttendanceService attendance;
    private final RiskService risks;
    private final ClassSessionRepository sessionRepository;
    private final ExamRepository examRepository;
    private final AttendanceRepository attendanceRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AcademicController(ReferenceService references, TimetableService timetable, ExamService exams, GradeService grades,
                              AttendanceService attendance, RiskService risks, ClassSessionRepository sessionRepository,
                              ExamRepository examRepository, AttendanceRepository attendanceRepository, CurrentUser currentUser,
                              Clock clock) {
        this.references = references;
        this.timetable = timetable;
        this.exams = exams;
        this.grades = grades;
        this.attendance = attendance;
        this.risks = risks;
        this.sessionRepository = sessionRepository;
        this.examRepository = examRepository;
        this.attendanceRepository = attendanceRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ------------------------------------------------------------- référentiel
    @GetMapping("/rooms")
    public List<RoomResponse> rooms() {
        return references.rooms();
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGERS)
    public RoomResponse createRoom(@Valid @RequestBody RoomRequest r) {
        return references.createRoom(r);
    }

    @PutMapping("/rooms/{id}")
    @PreAuthorize(MANAGERS)
    public RoomResponse updateRoom(@PathVariable Long id, @Valid @RequestBody RoomRequest r) {
        return references.updateRoom(id, r);
    }

    @GetMapping("/modules")
    @Operation(summary = "Modules, filtrables par filière ou enseignant (?mine=true : mes modules)")
    public List<ModuleResponse> modules(@RequestParam(required = false) Long filiereId, @RequestParam(required = false) Long teacherId,
                                        @RequestParam(defaultValue = "false") boolean mine) {
        return references.modules(filiereId, mine ? currentUser.get().getId() : teacherId);
    }

    @PostMapping("/modules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGERS)
    public ModuleResponse createModule(@Valid @RequestBody ModuleRequest r) {
        return references.createModule(r);
    }

    @PutMapping("/modules/{id}")
    @PreAuthorize(MANAGERS)
    public ModuleResponse updateModule(@PathVariable Long id, @Valid @RequestBody ModuleRequest r) {
        return references.updateModule(id, r);
    }

    // ------------------------------------------------------------- emploi du temps
    @GetMapping("/sessions")
    @Operation(summary = "Semaine contenant la date donnée, par filière, enseignant ou salle")
    public List<SessionResponse> sessions(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                                          @RequestParam(required = false) Long filiereId, @RequestParam(required = false) Long teacherId,
                                          @RequestParam(required = false) Long roomId) {
        return timetable.week(week, filiereId, teacherId, roomId);
    }

    @GetMapping("/sessions/me")
    @Operation(summary = "Mon emploi du temps (étudiant : sa filière ; enseignant : ses cours)")
    public List<SessionResponse> mySessions(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return timetable.mine(currentUser.get(), week);
    }

    @GetMapping("/sessions/{id}")
    public SessionResponse session(@PathVariable Long id) {
        return timetable.get(id);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Créer une séance ou une série hebdomadaire (weeks) ; 409 si salle, enseignant ou filière occupés")
    public List<SessionResponse> createSession(@Valid @RequestBody SessionRequest r) {
        return timetable.create(r);
    }

    @PutMapping("/sessions/{id}")
    @PreAuthorize(MANAGERS)
    public SessionResponse updateSession(@PathVariable Long id, @Valid @RequestBody SessionRequest r) {
        return timetable.update(id, r);
    }

    @PatchMapping("/sessions/{id}/cancel")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Annuler une séance : les étudiants sont prévenus")
    public SessionResponse cancelSession(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return timetable.cancel(id, body == null ? null : body.get("reason"));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGERS)
    public void deleteSession(@PathVariable Long id) {
        timetable.delete(id);
    }

    @PostMapping("/planning/timetable/preview")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Proposition automatique d'emploi du temps pour une semaine (rien n'est enregistré)")
    public PlanPreview previewTimetable(@Valid @RequestBody PlanPreviewRequest r) {
        return timetable.preview(r);
    }

    @PostMapping("/planning/timetable/apply")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Enregistrer la proposition, répétée sur N semaines ; les étudiants sont prévenus")
    public PlanApplyResult applyTimetable(@Valid @RequestBody PlanApplyRequest r) {
        return timetable.apply(r);
    }

    // ------------------------------------------------------------- examens
    @GetMapping("/exams")
    public List<ExamResponse> exams(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) Long filiereId) {
        return exams.list(from, to, filiereId);
    }

    @GetMapping("/exams/me")
    @Operation(summary = "Mes examens (étudiant) ou mes examens et surveillances (enseignant)")
    public List<ExamResponse> myExams() {
        return exams.mine(currentUser.get());
    }

    @PostMapping("/exams")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGERS)
    public ExamResponse createExam(@Valid @RequestBody ExamRequest r) {
        return exams.create(r);
    }

    @PutMapping("/exams/{id}")
    @PreAuthorize(MANAGERS)
    public ExamResponse updateExam(@PathVariable Long id, @Valid @RequestBody ExamRequest r) {
        return exams.update(id, r);
    }

    @DeleteMapping("/exams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGERS)
    public void deleteExam(@PathVariable Long id) {
        exams.delete(id);
    }

    @PostMapping("/planning/exams/preview")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Proposition automatique du calendrier d'examens d'une filière (salles et surveillants inclus)")
    public ExamPlanPreview previewExams(@Valid @RequestBody ExamPlanRequest r) {
        return exams.preview(r);
    }

    @PostMapping("/planning/exams/apply")
    @PreAuthorize(MANAGERS)
    public List<ExamResponse> applyExams(@Valid @RequestBody ExamPlanApplyRequest r) {
        return exams.applyPlan(r);
    }

    // ------------------------------------------------------------- notes
    @GetMapping("/grades/modules/{moduleId}")
    @PreAuthorize(STAFF)
    @Operation(summary = "Feuille de notes d'un module (enseignant du module ou chef)")
    public ModuleGrades moduleGrades(@PathVariable Long moduleId) {
        return grades.roster(currentUser.get(), moduleId);
    }

    @PutMapping("/grades/modules/{moduleId}")
    @PreAuthorize(STAFF)
    public ModuleGrades saveGrades(@PathVariable Long moduleId, @Valid @RequestBody GradeBatchRequest r) {
        return grades.save(currentUser.get(), moduleId, r);
    }

    @PostMapping("/grades/modules/{moduleId}/publish")
    @PreAuthorize(STAFF)
    @Operation(summary = "Publier une composante (CC, EXAM, TP, RATTRAPAGE) : chaque étudiant est notifié")
    public ModuleGrades publishGrades(@PathVariable Long moduleId, @RequestParam Assessment assessment) {
        return grades.publish(currentUser.get(), moduleId, assessment);
    }

    @GetMapping("/grades/me")
    @Operation(summary = "Mon relevé de notes (notes publiées)")
    public Transcript myTranscript() {
        User me = currentUser.get();
        return grades.transcript(me, me.getId());
    }

    @GetMapping("/grades/students/{studentId}")
    @PreAuthorize(STAFF)
    public Transcript transcript(@PathVariable Long studentId) {
        return grades.transcript(currentUser.get(), studentId);
    }

    // ------------------------------------------------------------- absences
    @GetMapping("/sessions/{id}/attendance")
    @PreAuthorize(STAFF)
    public AttendanceSheet attendanceSheet(@PathVariable Long id) {
        return attendance.sheet(currentUser.get(), id);
    }

    @PutMapping("/sessions/{id}/attendance")
    @PreAuthorize(STAFF)
    @Operation(summary = "Faire l'appel ; avertissement automatique au seuil d'absences non justifiées")
    public AttendanceSheet saveAttendance(@PathVariable Long id, @Valid @RequestBody AttendanceBatchRequest r) {
        return attendance.save(currentUser.get(), id, r);
    }

    @GetMapping("/absences/me")
    public List<AbsenceResponse> myAbsences() {
        return attendance.mine(currentUser.get());
    }

    @GetMapping("/absences/students/{studentId}")
    @PreAuthorize(STAFF)
    public List<AbsenceResponse> studentAbsences(@PathVariable Long studentId) {
        return attendance.forStudent(studentId);
    }

    @PatchMapping("/absences/{id}/justify")
    @Operation(summary = "Justifier une absence (étudiant) : le chef de département est notifié")
    public AbsenceResponse justify(@PathVariable Long id, @Valid @RequestBody JustifyRequest r) {
        return attendance.justify(currentUser.get(), id, r);
    }

    @GetMapping("/absences/pending")
    @PreAuthorize(MANAGERS)
    public List<AbsenceResponse> pendingJustifications() {
        return attendance.pending(currentUser.get());
    }

    @PatchMapping("/absences/{id}/review")
    @PreAuthorize(MANAGERS)
    public AbsenceResponse review(@PathVariable Long id, @Valid @RequestBody ReviewRequest r) {
        return attendance.review(currentUser.get(), id, r);
    }

    // ------------------------------------------------------------- suivi des étudiants
    @GetMapping("/risks")
    @PreAuthorize(STAFF)
    @Operation(summary = "Étudiants classés par risque de décrochage (notes, absences, jalons de PFE)")
    public List<RiskResponse> risks(@RequestParam(required = false) Long filiereId) {
        return risks.assess(filiereId);
    }

    @PostMapping("/risks/scan")
    @PreAuthorize(MANAGERS)
    @Operation(summary = "Prévenir maintenant les étudiants à risque élevé (sinon : chaque lundi à 7 h 30)")
    public RiskScanResult scan() {
        return risks.scan();
    }

    @GetMapping("/risks/{studentId}/explain")
    @PreAuthorize(STAFF)
    @Operation(summary = "Synthèse et actions conseillées par l'IA pour un étudiant")
    public RiskExplanation explain(@PathVariable Long studentId) {
        return risks.explain(studentId);
    }

    @GetMapping("/overview")
    @PreAuthorize(STAFF)
    public AcademicOverview overview() {
        LocalDate today = LocalDate.now(clock);
        LocalDate monday = AcademicRules.monday(today);
        List<RiskResponse> all = risks.assess(null);
        Double avg = all.stream().map(RiskResponse::average).filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().map(GradeCalculator::round).orElse(null);
        return new AcademicOverview(
                sessionRepository.countByDateAndStatus(today, SessionStatus.PLANNED),
                sessionRepository.findPlannedBetween(monday, monday.plusDays(6)).size(),
                examRepository.findBetween(today, today.plusDays(14), null).size(),
                attendanceRepository.findPendingJustifications().size(),
                (int) all.stream().filter(r -> r.level() == RiskLevel.HIGH).count(),
                avg);
    }
}
