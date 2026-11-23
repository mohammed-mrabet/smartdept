package ma.ump.smartdept.pfe;

import jakarta.validation.constraints.*;
import ma.ump.smartdept.user.UserDtos.FiliereSummary;
import ma.ump.smartdept.user.UserDtos.UserSummary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** DTO du module PFE. */
public final class PfeDtos {

    private PfeDtos() {
    }

    static List<String> splitKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keywords.split(",")).map(String::trim).filter(k -> !k.isEmpty()).toList();
    }

    // ---------------------------------------------------------------- sujets

    public record SubjectRequest(
            @NotBlank(message = "Le titre est obligatoire") @Size(max = 200) String title,
            @NotBlank(message = "La description est obligatoire") @Size(min = 50, max = 8000, message = "La description doit faire entre 50 et 8000 caractères") String description,
            @Size(max = 500) String keywords,
            @NotNull(message = "Le type est obligatoire (ACADEMIQUE, ENTREPRISE, RECHERCHE)") SubjectType type,
            @Size(max = 150) String companyName,
            @Min(value = 1, message = "1 étudiant minimum") @Max(value = 2, message = "2 étudiants maximum (binôme)") int maxStudents,
            @NotNull(message = "La filière est obligatoire") Long filiereId,
            Long supervisorId,
            @Pattern(regexp = "\\d{4}-\\d{4}", message = "Année universitaire au format 2026-2027") String academicYear) {
    }

    public record RejectRequest(@NotBlank(message = "Le motif est obligatoire") @Size(max = 500) String reason) {
    }

    public record SubjectResponse(Long id, String title, String description, List<String> keywords, SubjectType type,
                                  String companyName, int maxStudents, SubjectStatus status, String rejectionReason,
                                  String academicYear, UserSummary supervisor, FiliereSummary filiere,
                                  long pendingApplications, long acceptedStudents, LocalDateTime createdAt) {
        public static SubjectResponse of(PfeSubject s, long pending, long accepted) {
            return new SubjectResponse(s.getId(), s.getTitle(), s.getDescription(), splitKeywords(s.getKeywords()),
                    s.getType(), s.getCompanyName(), s.getMaxStudents(), s.getStatus(), s.getRejectionReason(),
                    s.getAcademicYear(), UserSummary.of(s.getSupervisor()),
                    new FiliereSummary(s.getFiliere().getId(), s.getFiliere().getCode(), s.getFiliere().getName()),
                    pending, accepted, s.getCreatedAt());
        }
    }

    // ---------------------------------------------------------------- candidatures

    public record ApplicationRequest(
            @NotNull(message = "Le sujet est obligatoire") Long subjectId,
            @Min(value = 1, message = "Le rang va de 1 à 3") @Max(value = 3, message = "Le rang va de 1 à 3") int preferenceRank,
            @Size(max = 2000) String motivation) {
    }

    public record DecisionRequest(@Size(max = 500) String comment) {
    }

    public record SubjectRef(Long id, String title, SubjectStatus status, String supervisorName) {
        static SubjectRef of(PfeSubject s) {
            return new SubjectRef(s.getId(), s.getTitle(), s.getStatus(), s.getSupervisor().getFullName());
        }
    }

    public record ApplicationResponse(Long id, SubjectRef subject, UserSummary student, String studentNumber,
                                      int preferenceRank, String motivation, ApplicationStatus status,
                                      String decisionComment, LocalDateTime createdAt) {
        public static ApplicationResponse of(PfeApplication a) {
            return new ApplicationResponse(a.getId(), SubjectRef.of(a.getSubject()), UserSummary.of(a.getStudent()),
                    a.getStudent().getStudentNumber(), a.getPreferenceRank(), a.getMotivation(), a.getStatus(),
                    a.getDecisionComment(), a.getCreatedAt());
        }
    }

    public record AutoAssignResult(int assigned, List<String> details, List<UserSummary> studentsWithoutSubject) {
    }

    // ---------------------------------------------------------------- projets et jalons

    public record MilestoneRequest(
            @NotBlank(message = "Le titre est obligatoire") @Size(max = 150) String title,
            @Size(max = 1000) String description,
            @NotNull(message = "L'échéance est obligatoire") LocalDate dueDate) {
    }

    public record SubmitMilestoneRequest(
            @NotBlank(message = "Le lien du livrable est obligatoire")
            @Pattern(regexp = "https?://.+", message = "Le lien doit commencer par http:// ou https://")
            @Size(max = 500) String deliverableUrl,
            @Size(max = 1000) String comment) {
    }

    public record ReviewMilestoneRequest(
            @NotNull(message = "La décision est obligatoire (VALIDATED ou NEEDS_REVISION)") MilestoneStatus decision,
            @Size(max = 1000) String feedback,
            @DecimalMin(value = "0", message = "Note entre 0 et 20") @DecimalMax(value = "20", message = "Note entre 0 et 20") Double grade) {
    }

    public record ExternalSupervisorRequest(@Size(max = 150) String name,
                                            @Email(message = "E-mail invalide") @Size(max = 150) String email) {
    }

    public record MilestoneResponse(Long id, String title, String description, LocalDate dueDate, int orderIndex,
                                    MilestoneStatus status, String deliverableUrl, String submissionComment,
                                    LocalDateTime submittedAt, String feedback, Double grade, boolean late) {
        public static MilestoneResponse of(PfeMilestone m, LocalDate today) {
            return new MilestoneResponse(m.getId(), m.getTitle(), m.getDescription(), m.getDueDate(), m.getOrderIndex(),
                    m.getStatus(), m.getDeliverableUrl(), m.getSubmissionComment(), m.getSubmittedAt(), m.getFeedback(),
                    m.getGrade(), m.isLate(today));
        }
    }

    public record DefenseSummary(Long id, LocalDate defenseDate, LocalTime startTime, LocalTime endTime, String room,
                                 DefenseStatus status, Double grade, String mention) {
        static DefenseSummary of(PfeDefense d) {
            return d == null ? null : new DefenseSummary(d.getId(), d.getDefenseDate(), d.getStartTime(), d.getEndTime(),
                    d.getRoom(), d.getStatus(), d.getGrade(), d.getMention());
        }
    }

    public record ProjectResponse(Long id, Long subjectId, String subjectTitle, SubjectType subjectType,
                                  String companyName, List<UserSummary> students, UserSummary supervisor,
                                  String externalSupervisorName, String externalSupervisorEmail, ProjectStatus status,
                                  int progress, List<MilestoneResponse> milestones, DefenseSummary defense,
                                  LocalDateTime createdAt) {
        public static ProjectResponse of(PfeProject p, PfeDefense defense, LocalDate today) {
            return new ProjectResponse(p.getId(), p.getSubject().getId(), p.getSubject().getTitle(),
                    p.getSubject().getType(), p.getSubject().getCompanyName(),
                    p.getStudents().stream().map(UserSummary::of).toList(), UserSummary.of(p.getSupervisor()),
                    p.getExternalSupervisorName(), p.getExternalSupervisorEmail(), p.getStatus(), p.progress(),
                    p.getMilestones().stream().map(m -> MilestoneResponse.of(m, today)).toList(),
                    DefenseSummary.of(defense), p.getCreatedAt());
        }
    }

    // ---------------------------------------------------------------- soutenances

    public record DefenseRequest(
            @NotNull(message = "Le projet est obligatoire") Long projectId,
            @NotNull(message = "La date est obligatoire") LocalDate defenseDate,
            @NotNull(message = "L'heure de début est obligatoire") LocalTime startTime,
            @NotNull(message = "L'heure de fin est obligatoire") LocalTime endTime,
            @NotBlank(message = "La salle est obligatoire") @Size(max = 50) String room,
            @NotNull(message = "Le jury est obligatoire") @Size(min = 2, max = 5, message = "Le jury compte de 2 à 5 membres") List<Long> juryIds,
            @NotNull(message = "Le président du jury est obligatoire") Long presidentId) {
    }

    public record GradeRequest(
            @NotNull(message = "La note est obligatoire")
            @DecimalMin(value = "0", message = "Note entre 0 et 20") @DecimalMax(value = "20", message = "Note entre 0 et 20") Double grade,
            @Size(max = 1000) String comment) {
    }

    public record DefenseResponse(Long id, Long projectId, String subjectTitle, List<UserSummary> students,
                                  UserSummary supervisor, LocalDate defenseDate, LocalTime startTime, LocalTime endTime,
                                  String room, List<UserSummary> jury, UserSummary president, DefenseStatus status,
                                  Double grade, String mention, String juryComment) {
        public static DefenseResponse of(PfeDefense d) {
            PfeProject p = d.getProject();
            return new DefenseResponse(d.getId(), p.getId(), p.getSubject().getTitle(),
                    p.getStudents().stream().map(UserSummary::of).toList(), UserSummary.of(p.getSupervisor()),
                    d.getDefenseDate(), d.getStartTime(), d.getEndTime(), d.getRoom(),
                    d.getJury().stream().map(UserSummary::of).toList(), UserSummary.of(d.getPresident()),
                    d.getStatus(), d.getGrade(), d.getMention(), d.getJuryComment());
        }
    }

    /** Membre de jury proposé : score de proximité avec le sujet et disponibilité sur le créneau. */
    public record JurySuggestion(UserSummary teacher, double score, List<String> matchedKeywords, boolean available,
                                 String reason) {
    }

    // ---------------------------------------------------------------- conventions de stage

    public record ConventionRequest(
            @NotBlank(message = "L'entreprise est obligatoire") @Size(max = 150) String companyName,
            @NotBlank(message = "L'adresse est obligatoire") @Size(max = 255) String companyAddress,
            @Email(message = "E-mail invalide") @Size(max = 150) String companyEmail,
            @NotBlank(message = "Le tuteur est obligatoire") @Size(max = 150) String tutorName,
            @NotBlank(message = "L'e-mail du tuteur est obligatoire") @Email(message = "E-mail invalide") @Size(max = 150) String tutorEmail,
            @NotNull(message = "La date de début est obligatoire") LocalDate startDate,
            @NotNull(message = "La date de fin est obligatoire") LocalDate endDate,
            @NotBlank(message = "La mission est obligatoire") @Size(max = 5000) String missionDescription) {
    }

    public record ReviewConventionRequest(boolean approve, @Size(max = 500) String comment) {
    }

    public record ConventionResponse(Long id, UserSummary student, String studentNumber, String filiereCode,
                                     Long projectId, String projectTitle, String companyName, String companyAddress,
                                     String companyEmail, String tutorName, String tutorEmail, LocalDate startDate,
                                     LocalDate endDate, long durationWeeks, String missionDescription,
                                     ConventionStatus status, String reviewComment, LocalDateTime createdAt) {
        public static ConventionResponse of(InternshipConvention c) {
            long weeks = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(c.getStartDate(), c.getEndDate()) / 7);
            return new ConventionResponse(c.getId(), UserSummary.of(c.getStudent()), c.getStudent().getStudentNumber(),
                    c.getStudent().getFiliere() == null ? null : c.getStudent().getFiliere().getCode(),
                    c.getProject() == null ? null : c.getProject().getId(),
                    c.getProject() == null ? null : c.getProject().getSubject().getTitle(),
                    c.getCompanyName(), c.getCompanyAddress(), c.getCompanyEmail(), c.getTutorName(), c.getTutorEmail(),
                    c.getStartDate(), c.getEndDate(), weeks, c.getMissionDescription(), c.getStatus(),
                    c.getReviewComment(), c.getCreatedAt());
        }
    }

    // ---------------------------------------------------------------- tableau de bord

    public record PfeStats(Map<SubjectStatus, Long> subjectsByStatus, long students, long studentsWithProject,
                           long studentsWithoutProject, long pendingApplications,
                           Map<ProjectStatus, Long> projectsByStatus, long lateMilestones, long submittedMilestones,
                           long defensesScheduled, long defensesDone, Double averageGrade, long conventionsToReview) {
    }
}
