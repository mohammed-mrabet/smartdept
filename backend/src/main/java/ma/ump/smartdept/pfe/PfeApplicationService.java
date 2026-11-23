package ma.ump.smartdept.pfe;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.pfe.PfeDtos.*;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserDtos.UserSummary;
import ma.ump.smartdept.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

import static ma.ump.smartdept.pfe.PfeRules.*;

/**
 * Candidatures et affectation.
 * Un étudiant choisit jusqu'à 3 sujets publiés de sa filière, classés de 1 à 3.
 * L'encadrant (ou le chef) accepte : un projet est créé avec ses jalons, les autres candidatures
 * de l'étudiant sont retirées et, quand le sujet est complet, les candidatures restantes sont refusées.
 * Le chef peut aussi lancer l'affectation automatique (rang 1 d'abord, puis 2, puis 3 ; premier arrivé, premier servi).
 */
@Service
@Transactional
public class PfeApplicationService {

    static final int MAX_ACTIVE_APPLICATIONS = 3;

    private final PfeApplicationRepository applicationRepository;
    private final PfeSubjectRepository subjectRepository;
    private final PfeProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationService notifications;
    private final Clock clock;

    public PfeApplicationService(PfeApplicationRepository applicationRepository, PfeSubjectRepository subjectRepository,
                                 PfeProjectRepository projectRepository, UserRepository userRepository,
                                 NotificationService notifications, Clock clock) {
        this.applicationRepository = applicationRepository;
        this.subjectRepository = subjectRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------- côté étudiant ----------

    public ApplicationResponse apply(User student, ApplicationRequest r) {
        if (student.getRole() != Role.ETUDIANT) {
            throw ApiException.forbidden("Seuls les étudiants peuvent candidater");
        }
        PfeSubject subject = subjectRepository.findById(r.subjectId())
                .orElseThrow(() -> ApiException.notFound("Sujet introuvable : " + r.subjectId()));
        if (subject.getStatus() != SubjectStatus.PUBLISHED) {
            throw ApiException.conflict("Ce sujet n'est pas ouvert aux candidatures");
        }
        if (student.getFiliere() == null || !student.getFiliere().getId().equals(subject.getFiliere().getId())) {
            throw ApiException.badRequest("Ce sujet est réservé à la filière " + subject.getFiliere().getCode());
        }
        if (projectRepository.existsByStudentId(student.getId())) {
            throw ApiException.conflict("Vous êtes déjà affecté à un projet de PFE");
        }
        if (applicationRepository.existsBySubjectIdAndStudentId(subject.getId(), student.getId())) {
            throw ApiException.conflict("Vous avez déjà candidaté à ce sujet");
        }
        List<PfeApplication> active = applicationRepository.findByStudentIdAndStatus(student.getId(), ApplicationStatus.PENDING);
        if (active.size() >= MAX_ACTIVE_APPLICATIONS) {
            throw ApiException.conflict("Vous avez déjà " + MAX_ACTIVE_APPLICATIONS + " candidatures en cours : retirez-en une d'abord");
        }
        if (active.stream().anyMatch(a -> a.getPreferenceRank() == r.preferenceRank())) {
            throw ApiException.conflict("Vous avez déjà un choix n°" + r.preferenceRank() + " : choisissez un autre rang");
        }
        PfeApplication a = new PfeApplication();
        a.setSubject(subject);
        a.setStudent(student);
        a.setPreferenceRank(r.preferenceRank());
        a.setMotivation(clean(r.motivation()));
        PfeApplication saved = applicationRepository.save(a);
        notifications.notify(subject.getSupervisor(), NotificationType.APPLICATION_RECEIVED, "Nouvelle candidature",
                student.getFullName() + " candidate au sujet « " + subject.getTitle() + " » (choix n°" + r.preferenceRank() + ").",
                "/pfe/sujets/" + subject.getId());
        return ApplicationResponse.of(saved);
    }

    public ApplicationResponse withdraw(User student, Long id) {
        PfeApplication a = find(id);
        if (!a.getStudent().getId().equals(student.getId())) {
            throw ApiException.forbidden("Cette candidature ne vous appartient pas");
        }
        if (a.getStatus() != ApplicationStatus.PENDING) {
            throw ApiException.conflict("Seule une candidature en attente peut être retirée");
        }
        a.setStatus(ApplicationStatus.WITHDRAWN);
        a.setDecisionComment("Retirée par l'étudiant");
        return ApplicationResponse.of(applicationRepository.save(a));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> mine(User student) {
        return applicationRepository.findByStudentIdOrderByPreferenceRankAsc(student.getId()).stream()
                .map(ApplicationResponse::of).toList();
    }

    // ---------- côté encadrant / chef ----------

    @Transactional(readOnly = true)
    public List<ApplicationResponse> forSubject(User me, Long subjectId) {
        PfeSubject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> ApiException.notFound("Sujet introuvable : " + subjectId));
        requireSupervisorOrManager(me, subject.getSupervisor());
        return applicationRepository.findBySubjectIdOrderByPreferenceRankAscCreatedAtAsc(subjectId).stream()
                .map(ApplicationResponse::of).toList();
    }

    public ApplicationResponse accept(User me, Long id, DecisionRequest r) {
        PfeApplication a = find(id);
        requireSupervisorOrManager(me, a.getSubject().getSupervisor());
        acceptInternal(a, r == null ? null : clean(r.comment()));
        return ApplicationResponse.of(a);
    }

    public ApplicationResponse reject(User me, Long id, DecisionRequest r) {
        PfeApplication a = find(id);
        requireSupervisorOrManager(me, a.getSubject().getSupervisor());
        if (a.getStatus() != ApplicationStatus.PENDING) {
            throw ApiException.conflict("Seule une candidature en attente peut être refusée");
        }
        a.setStatus(ApplicationStatus.REJECTED);
        a.setDecisionComment(r == null ? null : clean(r.comment()));
        notifications.notify(a.getStudent(), NotificationType.APPLICATION_REJECTED, "Candidature non retenue",
                "Votre candidature au sujet « " + a.getSubject().getTitle() + " » n'a pas été retenue"
                        + (a.getDecisionComment() == null ? "." : " : " + a.getDecisionComment()),
                "/pfe/candidatures");
        return ApplicationResponse.of(applicationRepository.save(a));
    }

    /** Affectation automatique de toutes les candidatures en attente. */
    public AutoAssignResult autoAssign(User me) {
        requireManager(me);
        List<PfeApplication> pending = applicationRepository.findByStatusAndSubjectStatusInOrderByPreferenceRankAscCreatedAtAscIdAsc(
                ApplicationStatus.PENDING, List.of(SubjectStatus.PUBLISHED));
        List<String> details = new ArrayList<>();
        for (PfeApplication a : pending) {
            if (a.getStatus() != ApplicationStatus.PENDING || a.getSubject().getStatus() != SubjectStatus.PUBLISHED
                    || projectRepository.existsByStudentId(a.getStudent().getId())) {
                continue;
            }
            acceptInternal(a, "Affectation automatique (choix n°" + a.getPreferenceRank() + ")");
            details.add(a.getStudent().getFullName() + " → « " + a.getSubject().getTitle() + " » (choix n°"
                    + a.getPreferenceRank() + ")");
        }
        List<UserSummary> without = userRepository.findByRoleAndEnabledTrueOrderByLastNameAsc(Role.ETUDIANT).stream()
                .filter(s -> !projectRepository.existsByStudentId(s.getId()))
                .map(UserSummary::of).toList();
        return new AutoAssignResult(details.size(), details, without);
    }

    // ---------- règle d'acceptation ----------

    void acceptInternal(PfeApplication a, String comment) {
        PfeSubject subject = a.getSubject();
        User student = a.getStudent();
        if (a.getStatus() != ApplicationStatus.PENDING) {
            throw ApiException.conflict("Seule une candidature en attente peut être acceptée");
        }
        if (subject.getStatus() != SubjectStatus.PUBLISHED) {
            throw ApiException.conflict("Le sujet n'accepte plus d'étudiants (statut : " + subject.getStatus() + ")");
        }
        if (projectRepository.existsByStudentId(student.getId())) {
            throw ApiException.conflict(student.getFullName() + " est déjà affecté à un autre projet");
        }
        PfeProject project = projectRepository.findBySubjectId(subject.getId()).orElse(null);
        boolean newProject = project == null;
        if (newProject) {
            project = newProject(subject);
        } else if (project.getStudents().size() >= subject.getMaxStudents()) {
            throw ApiException.conflict("Le sujet est complet (" + subject.getMaxStudents() + " étudiant(s))");
        }
        project.getStudents().add(student);
        projectRepository.save(project);

        a.setStatus(ApplicationStatus.ACCEPTED);
        a.setDecisionComment(comment);
        applicationRepository.save(a);

        // Les autres candidatures de l'étudiant sont retirées
        for (PfeApplication other : applicationRepository.findByStudentIdAndStatus(student.getId(), ApplicationStatus.PENDING)) {
            other.setStatus(ApplicationStatus.WITHDRAWN);
            other.setDecisionComment("Étudiant affecté au sujet « " + subject.getTitle() + " »");
        }

        notifications.notify(student, NotificationType.APPLICATION_ACCEPTED, "Candidature acceptée 🎉",
                "Vous êtes affecté au sujet « " + subject.getTitle() + " », encadré par "
                        + subject.getSupervisor().getFullName() + ". Votre espace projet et vos jalons sont prêts.",
                "/pfe/mon-projet");
        if (newProject) {
            notifications.notify(subject.getSupervisor(), NotificationType.PROJECT_CREATED, "Nouveau projet à encadrer",
                    student.getFullName() + " est affecté à votre sujet « " + subject.getTitle() + " ».",
                    "/pfe/projets/" + project.getId());
        }

        // Sujet complet : les candidatures restantes sont refusées
        if (project.getStudents().size() >= subject.getMaxStudents()) {
            subject.setStatus(SubjectStatus.ASSIGNED);
            subjectRepository.save(subject);
            for (PfeApplication other : applicationRepository.findBySubjectIdAndStatus(subject.getId(), ApplicationStatus.PENDING)) {
                other.setStatus(ApplicationStatus.REJECTED);
                other.setDecisionComment("Sujet complet");
                notifications.notify(other.getStudent(), NotificationType.APPLICATION_REJECTED, "Sujet complet",
                        "Le sujet « " + subject.getTitle() + " » est désormais complet. Vos autres choix restent actifs.",
                        "/pfe/candidatures");
            }
        }
    }

    /** Projet avec les 4 jalons standards, échelonnés à partir d'aujourd'hui. */
    private PfeProject newProject(PfeSubject subject) {
        PfeProject p = new PfeProject();
        p.setSubject(subject);
        p.setSupervisor(subject.getSupervisor());
        LocalDate today = LocalDate.now(clock);
        addMilestone(p, 1, "Cahier des charges", "Contexte, besoins, périmètre et planning prévisionnel validés avec l'encadrant.", today.plusWeeks(3));
        addMilestone(p, 2, "Conception", "Diagrammes UML, architecture technique et modèle de données.", today.plusWeeks(7));
        addMilestone(p, 3, "Rapport d'avancement et démo", "Démonstration intermédiaire et rapport d'avancement.", today.plusWeeks(12));
        addMilestone(p, 4, "Rapport final et code source", "Rapport final, lien du dépôt Git et présentation de soutenance.", today.plusWeeks(18));
        return projectRepository.save(p);
    }

    private static void addMilestone(PfeProject p, int order, String title, String description, LocalDate due) {
        PfeMilestone m = new PfeMilestone();
        m.setProject(p);
        m.setOrderIndex(order);
        m.setTitle(title);
        m.setDescription(description);
        m.setDueDate(due);
        p.getMilestones().add(m);
    }

    private PfeApplication find(Long id) {
        return applicationRepository.findById(id).orElseThrow(() -> ApiException.notFound("Candidature introuvable : " + id));
    }
}
