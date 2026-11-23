package ma.ump.smartdept.academic;

import ma.ump.smartdept.academic.AcademicDtos.*;
import ma.ump.smartdept.ai.AiClient;
import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationRepository;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.pfe.PfeProjectRepository;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserDtos.UserSummary;
import ma.ump.smartdept.user.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Détection des étudiants en difficulté. Score de 0 à 100 :
 * moyenne sous 10 (jusqu'à 40 points), modules ajournés (10 par module, 25 max),
 * absences non justifiées (4 par absence, 25 max), jalons de PFE en retard (5 par jalon, 10 max).
 * Risque élevé à partir de 50, moyen à partir de 25. Scan automatique chaque lundi matin.
 */
@Service
@Transactional(readOnly = true)
public class RiskService {

    private final UserRepository userRepository;
    private final GradeService gradeService;
    private final AttendanceRepository attendanceRepository;
    private final PfeProjectRepository projectRepository;
    private final NotificationService notifications;
    private final NotificationRepository notificationRepository;
    private final AiClient ai;
    private final Clock clock;

    public RiskService(UserRepository userRepository, GradeService gradeService, AttendanceRepository attendanceRepository,
                       PfeProjectRepository projectRepository, NotificationService notifications,
                       NotificationRepository notificationRepository, AiClient ai, Clock clock) {
        this.userRepository = userRepository;
        this.gradeService = gradeService;
        this.attendanceRepository = attendanceRepository;
        this.projectRepository = projectRepository;
        this.notifications = notifications;
        this.notificationRepository = notificationRepository;
        this.ai = ai;
        this.clock = clock;
    }

    public List<RiskResponse> assess(Long filiereId) {
        Map<Long, long[]> attendance = new HashMap<>();
        for (Object[] r : attendanceRepository.statsByStudent()) {
            attendance.put((Long) r[0], new long[]{((Number) r[1]).longValue(), r[2] == null ? 0 : ((Number) r[2]).longValue()});
        }
        LocalDate today = LocalDate.now(clock);
        List<RiskResponse> out = new ArrayList<>();
        for (User s : userRepository.findByRoleAndEnabledTrueOrderByLastNameAsc(Role.ETUDIANT)) {
            if (filiereId != null && (s.getFiliere() == null || !s.getFiliere().getId().equals(filiereId))) continue;
            Transcript t = gradeService.build(s, false);
            int failing = (int) t.semesters().stream().flatMap(x -> x.lines().stream()).filter(l -> "AJOURNE".equals(l.status())).count();
            long[] att = attendance.getOrDefault(s.getId(), new long[]{0, 0});
            int late = projectRepository.findByStudentId(s.getId())
                    .map(p -> (int) p.getMilestones().stream().filter(m -> m.isLate(today)).count()).orElse(0);
            out.add(compute(s, t.overallAverage(), failing, (int) att[1], att[0], late));
        }
        out.sort(Comparator.comparingInt(RiskResponse::score).reversed().thenComparing(r -> r.student().fullName()));
        return out;
    }

    static RiskResponse compute(User s, Double average, int failing, int unjustified, long recorded, int lateMilestones) {
        List<String> reasons = new ArrayList<>();
        int score = 0;
        if (average != null && average < GradeCalculator.PASS) {
            int p = (int) Math.min(40, Math.round((GradeCalculator.PASS - average) * 6));
            score += p;
            reasons.add("Moyenne générale de " + average + "/20");
        }
        if (failing > 0) {
            score += Math.min(25, failing * 10);
            reasons.add(failing + " module(s) ajourné(s)");
        }
        if (unjustified > 0) {
            score += Math.min(25, unjustified * 4);
            reasons.add(unjustified + " absence(s) non justifiée(s)");
        }
        if (lateMilestones > 0) {
            score += Math.min(10, lateMilestones * 5);
            reasons.add(lateMilestones + " jalon(s) de PFE en retard");
        }
        score = Math.min(100, score);
        RiskLevel level = score >= 50 ? RiskLevel.HIGH : score >= 25 ? RiskLevel.MEDIUM : RiskLevel.LOW;
        Double rate = recorded == 0 ? null : GradeCalculator.round(100.0 * (recorded - unjustified) / recorded);
        if (reasons.isEmpty()) reasons.add("Aucun signal d'alerte");
        return new RiskResponse(UserSummary.of(s), s.getStudentNumber(), s.getFiliere() == null ? null : s.getFiliere().getCode(),
                score, level, average, failing, unjustified, rate, lateMilestones, reasons);
    }

    /** Prévient les étudiants à risque élevé (au plus une fois par semaine) et envoie une synthèse au chef. */
    @Transactional
    public RiskScanResult scan() {
        List<RiskResponse> risks = assess(null);
        int high = 0, medium = 0, notified = 0;
        LocalDateTime weekAgo = LocalDateTime.now(clock).minusDays(6);
        for (RiskResponse r : risks) {
            if (r.level() == RiskLevel.MEDIUM) medium++;
            if (r.level() != RiskLevel.HIGH) continue;
            high++;
            if (notificationRepository.existsByRecipientIdAndTypeAndLinkAndCreatedAtAfter(r.student().id(), NotificationType.RISK_ALERT, "/notes", weekAgo)) continue;
            userRepository.findById(r.student().id()).ifPresent(u -> notifications.notify(u, NotificationType.RISK_ALERT,
                    "Faisons le point sur votre semestre",
                    "Quelques signaux nous inquiètent (" + String.join(", ", r.reasons()).toLowerCase()
                            + "). Votre enseignant référent et le chef de département peuvent vous aider : n'hésitez pas à les contacter.",
                    "/notes"));
            notified++;
        }
        if (high + medium > 0) {
            notifications.notifyManagers(NotificationType.RISK_ALERT, "Suivi des étudiants : " + high + " à risque élevé",
                    high + " étudiant(s) à risque élevé et " + medium + " à risque moyen cette semaine.", "/suivi-etudiants");
        }
        return new RiskScanResult(high, medium, notified);
    }

    @Scheduled(cron = "${app.academic.risk-cron:0 30 7 * * MON}")
    @Transactional
    public void weeklyScan() {
        scan();
    }

    /** Synthèse rédigée par l'IA (ou par règles) avec des actions concrètes pour l'équipe pédagogique. */
    public RiskExplanation explain(Long studentId) {
        RiskResponse r = assess(null).stream().filter(x -> x.student().id().equals(studentId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Étudiant introuvable : " + studentId));
        String facts = r.student().fullName() + " (" + r.filiereCode() + ") : score " + r.score() + "/100, "
                + String.join(", ", r.reasons()) + (r.attendanceRate() == null ? "" : ", assiduité " + r.attendanceRate() + " %") + ".";
        return ai.chat("Tu es conseiller pédagogique. En 5 phrases maximum, en français, explique la situation et propose 3 actions "
                        + "concrètes et bienveillantes (tutorat, rendez-vous, plan de rattrapage). Pas de jugement sur la personne.", facts)
                .map(text -> new RiskExplanation(text.trim(), "ia"))
                .orElseGet(() -> new RiskExplanation(localAdvice(r, facts), "local"));
    }

    static String localAdvice(RiskResponse r, String facts) {
        List<String> actions = new ArrayList<>();
        if (r.average() != null && r.average() < GradeCalculator.PASS) actions.add("proposer un tutorat sur les modules les plus faibles");
        if (r.failingModules() > 0) actions.add("préparer un plan de rattrapage module par module");
        if (r.unjustifiedAbsences() > 0) actions.add("organiser un entretien pour comprendre les absences");
        if (r.lateMilestones() > 0) actions.add("faire le point avec l'encadrant de PFE sur le calendrier des livrables");
        if (actions.isEmpty()) actions.add("poursuivre le suivi habituel");
        return facts + " Actions suggérées : " + String.join(" ; ", actions) + ".";
    }
}
