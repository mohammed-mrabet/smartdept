package ma.ump.smartdept.pfe;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.pfe.PfeDtos.*;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static ma.ump.smartdept.pfe.PfeRules.*;

/**
 * Suivi des projets : les étudiants déposent le lien de chaque livrable, l'encadrant le valide
 * ou demande une révision (avec commentaire et note facultative). Chaque étape notifie l'autre partie.
 */
@Service
@Transactional
public class PfeProjectService {

    private final PfeProjectRepository projectRepository;
    private final PfeMilestoneRepository milestoneRepository;
    private final PfeDefenseRepository defenseRepository;
    private final NotificationService notifications;
    private final Clock clock;

    public PfeProjectService(PfeProjectRepository projectRepository, PfeMilestoneRepository milestoneRepository,
                             PfeDefenseRepository defenseRepository, NotificationService notifications, Clock clock) {
        this.projectRepository = projectRepository;
        this.milestoneRepository = milestoneRepository;
        this.defenseRepository = defenseRepository;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------- consultation ----------

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(User me) {
        List<PfeProject> projects;
        if (isManager(me)) {
            projects = projectRepository.findAllByOrderByCreatedAtDesc();
        } else if (me.getRole() == Role.ENSEIGNANT) {
            projects = projectRepository.findBySupervisorIdOrderByCreatedAtDesc(me.getId());
        } else {
            projects = projectRepository.findByStudentId(me.getId()).map(List::of).orElse(List.of());
        }
        return projects.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse mine(User student) {
        return projectRepository.findByStudentId(student.getId()).map(this::toResponse)
                .orElseThrow(() -> ApiException.notFound("Vous n'êtes pas encore affecté à un projet de PFE"));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(User me, Long id) {
        PfeProject p = find(id);
        requireAccess(me, p);
        return toResponse(p);
    }

    // ---------- encadrement ----------

    public ProjectResponse setExternalSupervisor(User me, Long id, ExternalSupervisorRequest r) {
        PfeProject p = find(id);
        requireAccess(me, p);
        p.setExternalSupervisorName(clean(r.name()));
        p.setExternalSupervisorEmail(clean(r.email()));
        return toResponse(projectRepository.save(p));
    }

    public ProjectResponse addMilestone(User me, Long projectId, MilestoneRequest r) {
        PfeProject p = find(projectId);
        requireSupervisorOrManager(me, p.getSupervisor());
        requireNotDefended(p);
        PfeMilestone m = new PfeMilestone();
        m.setProject(p);
        m.setTitle(r.title().trim());
        m.setDescription(clean(r.description()));
        m.setDueDate(r.dueDate());
        m.setOrderIndex(p.getMilestones().stream().mapToInt(PfeMilestone::getOrderIndex).max().orElse(0) + 1);
        p.getMilestones().add(m);
        PfeProject saved = projectRepository.save(p);
        notifications.notifyAll(saved.getStudents(), NotificationType.MILESTONE_DUE, "Nouveau jalon : " + m.getTitle(),
                "Votre encadrant a ajouté le jalon « " + m.getTitle() + " », à rendre avant le " + m.getDueDate() + ".",
                "/pfe/mon-projet");
        return toResponse(saved);
    }

    public ProjectResponse updateMilestone(User me, Long milestoneId, MilestoneRequest r) {
        PfeMilestone m = findMilestone(milestoneId);
        PfeProject p = m.getProject();
        requireSupervisorOrManager(me, p.getSupervisor());
        m.setTitle(r.title().trim());
        m.setDescription(clean(r.description()));
        m.setDueDate(r.dueDate());
        milestoneRepository.save(m);
        return toResponse(p);
    }

    public ProjectResponse deleteMilestone(User me, Long milestoneId) {
        PfeMilestone m = findMilestone(milestoneId);
        PfeProject p = m.getProject();
        requireSupervisorOrManager(me, p.getSupervisor());
        if (m.getStatus() != MilestoneStatus.TODO) {
            throw ApiException.conflict("Un jalon déjà rendu ne peut pas être supprimé");
        }
        p.getMilestones().remove(m);
        return toResponse(projectRepository.save(p));
    }

    /** Dépôt d'un livrable par un étudiant du projet. */
    public ProjectResponse submitMilestone(User me, Long milestoneId, SubmitMilestoneRequest r) {
        PfeMilestone m = findMilestone(milestoneId);
        PfeProject p = m.getProject();
        if (!p.hasMember(me)) {
            throw ApiException.forbidden("Seuls les étudiants du projet déposent les livrables");
        }
        requireNotDefended(p);
        if (m.getStatus() == MilestoneStatus.VALIDATED) {
            throw ApiException.conflict("Ce jalon est déjà validé");
        }
        m.setDeliverableUrl(r.deliverableUrl().trim());
        m.setSubmissionComment(clean(r.comment()));
        m.setSubmittedAt(LocalDateTime.now(clock));
        m.setStatus(MilestoneStatus.SUBMITTED);
        milestoneRepository.save(m);
        boolean late = m.getDueDate().isBefore(LocalDate.now(clock));
        notifications.notify(p.getSupervisor(), NotificationType.MILESTONE_SUBMITTED, "Livrable déposé : " + m.getTitle(),
                me.getFullName() + " a déposé « " + m.getTitle() + " » pour le projet « " + p.getSubject().getTitle() + " »"
                        + (late ? " (en retard, échéance " + m.getDueDate() + ")." : "."),
                "/pfe/projets/" + p.getId());
        return toResponse(p);
    }

    /** Validation ou demande de révision par l'encadrant. */
    public ProjectResponse reviewMilestone(User me, Long milestoneId, ReviewMilestoneRequest r) {
        PfeMilestone m = findMilestone(milestoneId);
        PfeProject p = m.getProject();
        requireSupervisorOrManager(me, p.getSupervisor());
        if (r.decision() != MilestoneStatus.VALIDATED && r.decision() != MilestoneStatus.NEEDS_REVISION) {
            throw ApiException.badRequest("Décision attendue : VALIDATED ou NEEDS_REVISION");
        }
        if (m.getStatus() != MilestoneStatus.SUBMITTED) {
            throw ApiException.conflict("Le livrable n'a pas encore été déposé");
        }
        if (r.decision() == MilestoneStatus.NEEDS_REVISION && clean(r.feedback()) == null) {
            throw ApiException.badRequest("Expliquez ce qu'il faut corriger (champ feedback)");
        }
        m.setStatus(r.decision());
        m.setFeedback(clean(r.feedback()));
        m.setGrade(r.grade());
        milestoneRepository.save(m);
        boolean ok = r.decision() == MilestoneStatus.VALIDATED;
        notifications.notifyAll(p.getStudents(), NotificationType.MILESTONE_REVIEWED,
                (ok ? "Jalon validé : " : "Révision demandée : ") + m.getTitle(),
                ok ? "Votre encadrant a validé « " + m.getTitle() + " »" + (r.grade() == null ? "." : " (note : " + r.grade() + "/20).")
                        : "Votre encadrant demande des corrections sur « " + m.getTitle() + " » : " + m.getFeedback(),
                "/pfe/mon-projet");
        return toResponse(p);
    }

    /** L'encadrant déclare le projet prêt : le chef peut programmer la soutenance. */
    public ProjectResponse markReady(User me, Long id) {
        PfeProject p = find(id);
        requireSupervisorOrManager(me, p.getSupervisor());
        if (p.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw ApiException.conflict("Le projet est déjà " + (p.getStatus() == ProjectStatus.DEFENDED ? "soutenu" : "prêt pour la soutenance"));
        }
        long notValidated = p.getMilestones().stream().filter(m -> m.getStatus() != MilestoneStatus.VALIDATED).count();
        if (notValidated > 0) {
            throw ApiException.conflict(notValidated + " jalon(s) ne sont pas encore validés");
        }
        p.setStatus(ProjectStatus.READY_FOR_DEFENSE);
        PfeProject saved = projectRepository.save(p);
        notifications.notifyManagers(NotificationType.PROJECT_READY, "Projet prêt pour la soutenance",
                "Le projet « " + p.getSubject().getTitle() + " » est prêt : vous pouvez programmer la soutenance.",
                "/pfe/soutenances");
        notifications.notifyAll(p.getStudents(), NotificationType.PROJECT_READY, "Projet prêt pour la soutenance",
                "Votre encadrant a validé l'ensemble de vos livrables. La date de soutenance vous sera communiquée.",
                "/pfe/mon-projet");
        return toResponse(saved);
    }

    // ---------- outils ----------

    private void requireAccess(User me, PfeProject p) {
        if (!isManager(me) && !p.getSupervisor().getId().equals(me.getId()) && !p.hasMember(me)) {
            throw ApiException.forbidden("Vous n'avez pas accès à ce projet");
        }
    }

    private static void requireNotDefended(PfeProject p) {
        if (p.getStatus() == ProjectStatus.DEFENDED) {
            throw ApiException.conflict("Le projet est déjà soutenu");
        }
    }

    PfeProject find(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Projet introuvable : " + id));
    }

    private PfeMilestone findMilestone(Long id) {
        return milestoneRepository.findById(id).orElseThrow(() -> ApiException.notFound("Jalon introuvable : " + id));
    }

    ProjectResponse toResponse(PfeProject p) {
        return ProjectResponse.of(p, defenseRepository.findByProjectId(p.getId()).orElse(null), LocalDate.now(clock));
    }
}
