package ma.ump.smartdept.academic;

import ma.ump.smartdept.academic.AcademicDtos.*;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.user.*;
import ma.ump.smartdept.user.UserDtos.FiliereSummary;
import ma.ump.smartdept.user.UserDtos.UserSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static ma.ump.smartdept.academic.AcademicRules.*;

/** Saisie des notes par l'enseignant du module, publication, relevé de notes de l'étudiant. */
@Service
@Transactional
public class GradeService {

    private static final Map<Assessment, String> LABELS = Map.of(
            Assessment.CC, "contrôle continu", Assessment.EXAM, "examen", Assessment.TP, "travaux pratiques",
            Assessment.RATTRAPAGE, "rattrapage");

    private final GradeRepository gradeRepository;
    private final CourseModuleRepository moduleRepository;
    private final UserRepository userRepository;
    private final ReferenceService references;
    private final NotificationService notifications;

    public GradeService(GradeRepository gradeRepository, CourseModuleRepository moduleRepository, UserRepository userRepository,
                        ReferenceService references, NotificationService notifications) {
        this.gradeRepository = gradeRepository;
        this.moduleRepository = moduleRepository;
        this.userRepository = userRepository;
        this.references = references;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public ModuleGrades roster(User me, Long moduleId) {
        CourseModule m = references.module(moduleId);
        requireTeacherOf(me, m);
        List<User> students = userRepository.findByRoleAndFiliereIdAndEnabledTrue(Role.ETUDIANT, m.getFiliere().getId());
        Map<Long, Map<Assessment, Double>> byStudent = new HashMap<>();
        Map<Assessment, Boolean> published = new EnumMap<>(Assessment.class);
        for (Grade g : gradeRepository.findByModule(moduleId)) {
            byStudent.computeIfAbsent(g.getStudent().getId(), k -> new EnumMap<>(Assessment.class)).put(g.getAssessment(), g.getValue());
            published.merge(g.getAssessment(), g.isPublished(), Boolean::logicalAnd);
        }
        List<RosterRow> rows = new ArrayList<>();
        List<Double> averages = new ArrayList<>();
        for (User s : students) {
            Map<Assessment, Double> grades = byStudent.getOrDefault(s.getId(), new EnumMap<>(Assessment.class));
            Double avg = GradeCalculator.average(grades);
            if (avg != null) averages.add(avg);
            rows.add(new RosterRow(UserSummary.of(s), s.getStudentNumber(), grades, avg, GradeCalculator.status(avg, null)));
        }
        Double classAvg = averages.isEmpty() ? null : GradeCalculator.round(averages.stream().mapToDouble(d -> d).average().orElse(0));
        Double passRate = averages.isEmpty() ? null : GradeCalculator.round(100.0 * averages.stream().filter(a -> a >= GradeCalculator.PASS).count() / averages.size());
        return new ModuleGrades(ModuleResponse.of(m), rows, published, classAvg, passRate,
                averages.stream().min(Double::compare).orElse(null), averages.stream().max(Double::compare).orElse(null));
    }

    /** Enregistre une colonne de notes ; une valeur vide supprime la note. Les notes d'une composante déjà publiée restent publiées. */
    public ModuleGrades save(User me, Long moduleId, GradeBatchRequest r) {
        CourseModule m = references.module(moduleId);
        requireTeacherOf(me, m);
        Map<Long, User> students = userRepository.findByRoleAndFiliereIdAndEnabledTrue(Role.ETUDIANT, m.getFiliere().getId())
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Grade> existing = gradeRepository.findByModuleIdAndAssessment(moduleId, r.assessment()).stream()
                .collect(Collectors.toMap(g -> g.getStudent().getId(), g -> g));
        boolean alreadyPublished = !existing.isEmpty() && existing.values().stream().allMatch(Grade::isPublished);
        for (GradeEntry e : r.entries()) {
            User student = students.get(e.studentId());
            if (student == null) throw ApiException.badRequest("L'étudiant " + e.studentId() + " n'est pas inscrit dans la filière du module");
            Grade g = existing.get(e.studentId());
            if (e.value() == null) {
                if (g != null) gradeRepository.delete(g);
                continue;
            }
            if (g == null) {
                g = new Grade();
                g.setStudent(student);
                g.setModule(m);
                g.setAssessment(r.assessment());
                g.setPublished(alreadyPublished);
            }
            g.setValue(GradeCalculator.round(e.value()));
            g.setComment(e.comment() == null || e.comment().isBlank() ? null : e.comment().trim());
            gradeRepository.save(g);
        }
        gradeRepository.flush();
        return roster(me, moduleId);
    }

    public ModuleGrades publish(User me, Long moduleId, Assessment assessment) {
        CourseModule m = references.module(moduleId);
        requireTeacherOf(me, m);
        List<Grade> grades = gradeRepository.findByModuleIdAndAssessment(moduleId, assessment);
        if (grades.isEmpty()) throw ApiException.badRequest("Aucune note de " + LABELS.get(assessment) + " à publier");
        List<User> recipients = new ArrayList<>();
        for (Grade g : grades) {
            if (!g.isPublished()) recipients.add(g.getStudent());
            g.setPublished(true);
        }
        notifications.notifyAll(recipients, NotificationType.GRADES_PUBLISHED, "Note publiée : " + m.getCode(),
                "Votre note de " + LABELS.get(assessment) + " en " + m.getName() + " est disponible.", "/notes");
        return roster(me, moduleId);
    }

    @Transactional(readOnly = true)
    public Transcript transcript(User viewer, Long studentId) {
        User student = userRepository.findWithFiliere(studentId).orElseThrow(() -> ApiException.notFound("Étudiant introuvable : " + studentId));
        if (student.getRole() != Role.ETUDIANT) throw ApiException.badRequest(student.getFullName() + " n'est pas étudiant");
        boolean self = viewer.getId().equals(studentId);
        if (!self && viewer.getRole() == Role.ETUDIANT) throw ApiException.forbidden("Vous ne pouvez consulter que votre relevé");
        return build(student, self);
    }

    /** Relevé : notes publiées uniquement pour l'étudiant lui-même, toutes les notes pour les enseignants et le chef. */
    Transcript build(User student, boolean publishedOnly) {
        List<CourseModule> modules = student.getFiliere() == null ? List.of() : moduleRepository.search(student.getFiliere().getId(), null);
        Map<Long, Map<Assessment, Double>> byModule = new HashMap<>();
        for (Grade g : gradeRepository.findByStudent(student.getId(), publishedOnly)) {
            byModule.computeIfAbsent(g.getModule().getId(), k -> new EnumMap<>(Assessment.class)).put(g.getAssessment(), g.getValue());
        }
        Map<String, List<CourseModule>> bySemester = modules.stream()
                .collect(Collectors.groupingBy(CourseModule::getSemester, TreeMap::new, Collectors.toList()));
        List<SemesterSummary> semesters = new ArrayList<>();
        double weighted = 0, coefs = 0;
        int validated = 0, total = 0;
        for (Map.Entry<String, List<CourseModule>> e : bySemester.entrySet()) {
            double sw = 0, sc = 0;
            Map<Long, Double> avgs = new HashMap<>();
            for (CourseModule m : e.getValue()) {
                Double avg = GradeCalculator.average(byModule.getOrDefault(m.getId(), Map.of()));
                avgs.put(m.getId(), avg);
                if (avg != null) {
                    sw += avg * m.getCoefficient();
                    sc += m.getCoefficient();
                }
            }
            Double semAvg = sc == 0 ? null : GradeCalculator.round(sw / sc);
            List<TranscriptLine> lines = new ArrayList<>();
            int semValidated = 0;
            for (CourseModule m : e.getValue()) {
                Map<Assessment, Double> g = byModule.getOrDefault(m.getId(), Map.of());
                Double avg = avgs.get(m.getId());
                String status = GradeCalculator.status(avg, semAvg);
                if (status.equals("VALIDE") || status.equals("COMPENSE")) semValidated++;
                lines.add(new TranscriptLine(m.getId(), m.getCode(), m.getName(), m.getSemester(), m.getCoefficient(),
                        g.get(Assessment.CC), g.get(Assessment.EXAM), g.get(Assessment.TP), g.get(Assessment.RATTRAPAGE), avg, status));
            }
            semesters.add(new SemesterSummary(e.getKey(), semAvg, semValidated, lines.size(), lines));
            weighted += sw;
            coefs += sc;
            validated += semValidated;
            total += lines.size();
        }
        Double overall = coefs == 0 ? null : GradeCalculator.round(weighted / coefs);
        String decision = overall == null ? "En attente des notes"
                : validated == total ? "Admis" : overall >= GradeCalculator.PASS ? "Admis avec modules à rattraper" : "Rattrapage";
        return new Transcript(UserSummary.of(student), student.getStudentNumber(), FiliereSummary.of(student.getFiliere()),
                semesters, overall, validated, total, decision);
    }
}
