package ma.ump.smartdept.academic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ma.ump.smartdept.user.Filiere;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserDtos.FiliereSummary;
import ma.ump.smartdept.user.UserDtos.UserSummary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public final class AcademicDtos {

    private AcademicDtos() {
    }

    static UserSummary toUser(User u) {
        return u == null ? null : UserSummary.of(u);
    }

    static FiliereSummary toFiliere(Filiere f) {
        return FiliereSummary.of(f);
    }

    // ------------------------------------------------------------------ salles et modules

    public record RoomRequest(@NotBlank(message = "Le code est obligatoire") @Size(max = 30) String code,
                              @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String name,
                              @Min(value = 1, message = "Capacité d'au moins 1 place") @Max(1000) int capacity,
                              @NotNull(message = "Le type est obligatoire") RoomType type,
                              @Size(max = 100) String building, Boolean active) {
    }

    public record RoomResponse(Long id, String code, String name, int capacity, RoomType type, String building, boolean active) {
        public static RoomResponse of(Room r) {
            return new RoomResponse(r.getId(), r.getCode(), r.getName(), r.getCapacity(), r.getType(), r.getBuilding(), r.isActive());
        }
    }

    public record ModuleRequest(@NotBlank(message = "Le code est obligatoire") @Size(max = 20) String code,
                                @NotBlank(message = "Le nom est obligatoire") @Size(max = 150) String name,
                                @NotNull(message = "La filière est obligatoire") Long filiereId,
                                @NotBlank @Pattern(regexp = "S([1-9]|10)", message = "Semestre de S1 à S10") String semester,
                                Long teacherId,
                                @DecimalMin("0.5") @DecimalMax("10") double coefficient,
                                @Min(0) @Max(6) int cmPerWeek, @Min(0) @Max(6) int tdPerWeek, @Min(0) @Max(6) int tpPerWeek,
                                @Size(max = 500) String description) {
    }

    public record ModuleResponse(Long id, String code, String name, FiliereSummary filiere, String semester, UserSummary teacher,
                                 double coefficient, int cmPerWeek, int tdPerWeek, int tpPerWeek, String description) {
        public static ModuleResponse of(CourseModule m) {
            return new ModuleResponse(m.getId(), m.getCode(), m.getName(), toFiliere(m.getFiliere()), m.getSemester(),
                    toUser(m.getTeacher()), m.getCoefficient(), m.getCmPerWeek(), m.getTdPerWeek(), m.getTpPerWeek(), m.getDescription());
        }
    }

    // ------------------------------------------------------------------ emploi du temps

    public record SessionRequest(@NotNull(message = "Le module est obligatoire") Long moduleId,
                                 Long teacherId,
                                 @NotNull(message = "La salle est obligatoire") Long roomId,
                                 @NotNull(message = "La date est obligatoire") LocalDate date,
                                 @NotNull(message = "L'heure de début est obligatoire") LocalTime startTime,
                                 @NotNull(message = "L'heure de fin est obligatoire") LocalTime endTime,
                                 @NotNull(message = "Le type est obligatoire") SessionType type,
                                 @Size(max = 500) String notes,
                                 @Min(1) @Max(16) Integer weeks) {
    }

    public record RoomSummary(Long id, String code, String name, int capacity, RoomType type) {
        public static RoomSummary of(Room r) {
            return new RoomSummary(r.getId(), r.getCode(), r.getName(), r.getCapacity(), r.getType());
        }
    }

    public record SessionResponse(Long id, Long moduleId, String moduleCode, String moduleName, UserSummary teacher, RoomSummary room,
                                  FiliereSummary filiere, LocalDate date, LocalTime startTime, LocalTime endTime, SessionType type,
                                  SessionStatus status, String notes, boolean attendanceTaken) {
        public static SessionResponse of(ClassSession s) {
            return new SessionResponse(s.getId(), s.getModule().getId(), s.getModule().getCode(), s.getModule().getName(),
                    toUser(s.getTeacher()), RoomSummary.of(s.getRoom()), toFiliere(s.getFiliere()), s.getDate(), s.getStartTime(),
                    s.getEndTime(), s.getType(), s.getStatus(), s.getNotes(), s.isAttendanceTaken());
        }
    }

    public record PlanPreviewRequest(@NotNull(message = "La filière est obligatoire") Long filiereId,
                                     @NotNull(message = "La semaine est obligatoire") LocalDate weekStart,
                                     @Pattern(regexp = "S([1-9]|10)") String semester) {
    }

    public record PlannedSlot(Long moduleId, String moduleCode, String moduleName, Long teacherId, String teacherName,
                              Long roomId, String roomCode, LocalDate date, LocalTime startTime, LocalTime endTime,
                              SessionType type, String reason) {
    }

    public record PlanPreview(Long filiereId, LocalDate weekStart, int students, int required, List<PlannedSlot> slots,
                              List<String> unplaced, Map<String, Integer> sessionsPerDay) {
    }

    public record SlotInput(@NotNull Long moduleId, Long teacherId, @NotNull Long roomId, @NotNull LocalDate date,
                            @NotNull LocalTime startTime, @NotNull LocalTime endTime, @NotNull SessionType type) {
    }

    public record PlanApplyRequest(@NotNull Long filiereId, @Min(1) @Max(16) int weeks,
                                   @NotEmpty(message = "Aucune séance à créer") List<@Valid SlotInput> slots) {
    }

    public record PlanApplyResult(int created, int skipped, List<String> conflicts) {
    }

    // ------------------------------------------------------------------ examens

    public record ExamRequest(@NotNull(message = "Le module est obligatoire") Long moduleId,
                              @NotNull(message = "La date est obligatoire") LocalDate date,
                              @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                              @NotNull(message = "Le type est obligatoire") ExamType type,
                              @NotEmpty(message = "Choisissez au moins une salle") List<Long> roomIds,
                              List<Long> supervisorIds,
                              @Size(max = 500) String notes) {
    }

    public record ExamResponse(Long id, Long moduleId, String moduleCode, String moduleName, UserSummary moduleTeacher,
                               FiliereSummary filiere, LocalDate date, LocalTime startTime, LocalTime endTime, ExamType type,
                               List<RoomSummary> rooms, int capacity, int students, List<UserSummary> supervisors, String notes) {
        public static ExamResponse of(Exam e, int students) {
            return new ExamResponse(e.getId(), e.getModule().getId(), e.getModule().getCode(), e.getModule().getName(),
                    toUser(e.getModule().getTeacher()), toFiliere(e.getFiliere()), e.getDate(), e.getStartTime(), e.getEndTime(),
                    e.getType(), e.getRooms().stream().map(RoomSummary::of).toList(), e.capacity(), students,
                    e.getSupervisors().stream().map(UserSummary::of).toList(), e.getNotes());
        }
    }

    public record ExamPlanRequest(@NotNull Long filiereId, @NotNull LocalDate from, @NotNull LocalDate to,
                                  @Pattern(regexp = "S([1-9]|10)") String semester, ExamType type,
                                  @Min(60) @Max(240) Integer durationMinutes) {
    }

    public record ExamPlanSlot(Long moduleId, String moduleCode, String moduleName, LocalDate date, LocalTime startTime,
                               LocalTime endTime, List<Long> roomIds, List<String> roomCodes, List<Long> supervisorIds,
                               List<String> supervisorNames) {
    }

    public record ExamPlanPreview(Long filiereId, ExamType type, int students, List<ExamPlanSlot> slots, List<String> unplaced) {
    }

    public record ExamPlanApplyRequest(@NotNull Long filiereId, @NotNull ExamType type,
                                       @NotEmpty List<ExamPlanSlot> slots) {
    }

    // ------------------------------------------------------------------ notes

    public record GradeEntry(@NotNull Long studentId, @DecimalMin("0") @DecimalMax("20") Double value, @Size(max = 300) String comment) {
    }

    public record GradeBatchRequest(@NotNull(message = "La composante est obligatoire") Assessment assessment,
                                    @NotNull List<@Valid GradeEntry> entries) {
    }

    public record RosterRow(UserSummary student, String studentNumber, Map<Assessment, Double> grades, Double average, String status) {
    }

    public record ModuleGrades(ModuleResponse module, List<RosterRow> rows, Map<Assessment, Boolean> published,
                               Double classAverage, Double passRate, Double min, Double max) {
    }

    public record TranscriptLine(Long moduleId, String code, String name, String semester, double coefficient,
                                 Double cc, Double exam, Double tp, Double rattrapage, Double average, String status) {
    }

    public record SemesterSummary(String semester, Double average, int validated, int total, List<TranscriptLine> lines) {
    }

    public record Transcript(UserSummary student, String studentNumber, FiliereSummary filiere, List<SemesterSummary> semesters,
                             Double overallAverage, int validatedModules, int totalModules, String decision) {
    }

    // ------------------------------------------------------------------ absences

    public record AttendanceRow(Long studentId, String fullName, String studentNumber, AttendanceStatus status,
                                JustificationStatus justificationStatus) {
    }

    public record AttendanceSheet(SessionResponse session, List<AttendanceRow> rows, boolean taken) {
    }

    public record AttendanceEntry(@NotNull Long studentId, @NotNull AttendanceStatus status) {
    }

    public record AttendanceBatchRequest(@NotNull List<@Valid AttendanceEntry> entries) {
    }

    public record AbsenceResponse(Long id, Long sessionId, String moduleCode, String moduleName, LocalDate date,
                                  LocalTime startTime, LocalTime endTime, String teacherName, AttendanceStatus status,
                                  JustificationStatus justificationStatus, String justification, String reviewComment,
                                  LocalDateTime justifiedAt, UserSummary student) {
        public static AbsenceResponse of(Attendance a) {
            ClassSession s = a.getSession();
            return new AbsenceResponse(a.getId(), s.getId(), s.getModule().getCode(), s.getModule().getName(), s.getDate(),
                    s.getStartTime(), s.getEndTime(), s.getTeacher() == null ? null : s.getTeacher().getFullName(), a.getStatus(),
                    a.getJustificationStatus(), a.getJustification(), a.getReviewComment(), a.getJustifiedAt(), toUser(a.getStudent()));
        }
    }

    public record JustifyRequest(@NotBlank(message = "Expliquez le motif de l'absence") @Size(max = 1000) String reason) {
    }

    public record ReviewRequest(boolean accept, @Size(max = 300) String comment) {
    }

    // ------------------------------------------------------------------ suivi des étudiants

    public record RiskResponse(UserSummary student, String studentNumber, String filiereCode, int score, RiskLevel level,
                               Double average, int failingModules, int unjustifiedAbsences, Double attendanceRate,
                               int lateMilestones, List<String> reasons) {
    }

    public record RiskScanResult(int high, int medium, int notified) {
    }

    public record RiskExplanation(String text, String source) {
    }

    public record AcademicOverview(long sessionsToday, long sessionsThisWeek, long examsNext14Days, long pendingJustifications,
                                   int studentsAtRisk, Double departmentAverage) {
    }
}
