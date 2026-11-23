package ma.ump.smartdept.ai;

import ma.ump.smartdept.notification.NotificationRepository;
import ma.ump.smartdept.pfe.*;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Rassemble, en une transaction courte, ce que le chatbot doit savoir sur l'utilisateur. */
@Component
public class ChatContextBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UserRepository userRepository;
    private final PfeProjectRepository projectRepository;
    private final PfeApplicationRepository applicationRepository;
    private final PfeSubjectRepository subjectRepository;
    private final PfeMilestoneRepository milestoneRepository;
    private final PfeDefenseRepository defenseRepository;
    private final InternshipConventionRepository conventionRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public ChatContextBuilder(UserRepository userRepository, PfeProjectRepository projectRepository,
                              PfeApplicationRepository applicationRepository, PfeSubjectRepository subjectRepository,
                              PfeMilestoneRepository milestoneRepository, PfeDefenseRepository defenseRepository,
                              InternshipConventionRepository conventionRepository,
                              NotificationRepository notificationRepository, Clock clock) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.applicationRepository = applicationRepository;
        this.subjectRepository = subjectRepository;
        this.milestoneRepository = milestoneRepository;
        this.defenseRepository = defenseRepository;
        this.conventionRepository = conventionRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ChatContext build(Long userId) {
        User me = userRepository.findWithFiliere(userId).orElseThrow();
        LocalDate today = LocalDate.now(clock);
        List<String> facts = new ArrayList<>();
        Map<String, String> topics = new LinkedHashMap<>();
        facts.add("Utilisateur : " + me.getFullName() + ", rôle " + me.getRole()
                + (me.getFiliere() != null ? ", filière " + me.getFiliere().getCode() : ""));
        facts.add("Date du jour : " + today.format(DATE));

        long unread = notificationRepository.countByRecipientIdAndReadFalse(me.getId());
        facts.add("Notifications non lues : " + unread);
        topics.put("notification", unread == 0 ? "Vous n'avez aucune notification non lue."
                : "Vous avez " + unread + " notification(s) non lue(s) : cliquez sur la cloche en haut à droite.");

        switch (me.getRole()) {
            case ETUDIANT -> student(me, today, facts, topics);
            case ENSEIGNANT -> teacher(me, today, facts, topics);
            default -> manager(me, today, facts, topics);
        }
        return new ChatContext(me.getFirstName(), me.getRole(), facts, topics);
    }

    private void student(User me, LocalDate today, List<String> facts, Map<String, String> topics) {
        Optional<PfeProject> project = projectRepository.findByStudentId(me.getId());
        List<PfeApplication> apps = applicationRepository.findByStudentIdOrderByPreferenceRankAsc(me.getId());
        long pending = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.PENDING).count();
        topics.put("sujet", "Les sujets publiés pour votre filière sont dans « PFE › Sujets ». Vous pouvez candidater "
                + "à 3 sujets au maximum, classés de 1 à 3.");

        if (project.isEmpty()) {
            facts.add("Pas encore de projet de PFE affecté. Candidatures en attente : " + pending + "/3");
            StringBuilder sb = new StringBuilder(pending == 0
                    ? "Vous n'avez aucune candidature en cours. Choisissez jusqu'à 3 sujets dans « PFE › Sujets »."
                    : "Vous avez " + pending + " candidature(s) en attente :");
            apps.stream().filter(a -> a.getStatus() == ApplicationStatus.PENDING).forEach(a ->
                    sb.append("\n• choix n°").append(a.getPreferenceRank()).append(" : ").append(a.getSubject().getTitle()));
            topics.put("candidature", sb.toString());
            topics.put("jalon", "Les jalons apparaîtront quand vous serez affecté à un sujet.");
            topics.put("soutenance", "Votre soutenance sera programmée après l'affectation et la validation de vos livrables.");
        } else {
            PfeProject p = project.get();
            facts.add("Projet : « " + p.getSubject().getTitle() + " », encadré par " + p.getSupervisor().getFullName()
                    + ", avancement " + p.progress() + "%, statut " + p.getStatus());
            topics.put("candidature", "Vous êtes affecté au sujet « " + p.getSubject().getTitle() + " ».");
            Optional<PfeMilestone> next = p.getMilestones().stream()
                    .filter(m -> m.getStatus() == MilestoneStatus.TODO || m.getStatus() == MilestoneStatus.NEEDS_REVISION)
                    .min(Comparator.comparing(PfeMilestone::getDueDate));
            if (next.isPresent()) {
                PfeMilestone m = next.get();
                long days = java.time.temporal.ChronoUnit.DAYS.between(today, m.getDueDate());
                String when = days < 0 ? "en retard de " + (-days) + " jour(s)" : days == 0 ? "aujourd'hui" : "dans " + days + " jour(s)";
                facts.add("Prochain jalon : « " + m.getTitle() + " », échéance " + m.getDueDate().format(DATE) + " (" + when + ")"
                        + (m.getStatus() == MilestoneStatus.NEEDS_REVISION ? ", corrections demandées : " + m.getFeedback() : ""));
                topics.put("jalon", "Votre prochain jalon est « " + m.getTitle() + " », à rendre le " + m.getDueDate().format(DATE)
                        + " (" + when + "). Déposez le lien du livrable dans « PFE › Mon projet »."
                        + (m.getStatus() == MilestoneStatus.NEEDS_REVISION ? "\nCorrections demandées : " + m.getFeedback() : ""));
            } else {
                topics.put("jalon", "Tous vos jalons sont rendus. Avancement : " + p.progress() + " %.");
            }
            Optional<PfeDefense> defense = defenseRepository.findByProjectId(p.getId());
            if (defense.isPresent() && defense.get().getStatus() != DefenseStatus.CANCELLED) {
                PfeDefense d = defense.get();
                String text = d.getStatus() == DefenseStatus.DONE
                        ? "Votre soutenance a eu lieu : note " + d.getGrade() + "/20, mention " + d.getMention() + "."
                        : "Votre soutenance est prévue le " + d.getDefenseDate().format(DATE) + " de " + d.getStartTime()
                        + " à " + d.getEndTime() + ", salle " + d.getRoom() + ". Président du jury : "
                        + d.getPresident().getFullName() + ".";
                facts.add(text);
                topics.put("soutenance", text);
            } else {
                topics.put("soutenance", "Votre soutenance n'est pas encore programmée : elle le sera quand votre encadrant aura déclaré le projet prêt.");
            }
        }
        List<InternshipConvention> conventions = conventionRepository.findByStudentIdOrderByCreatedAtDesc(me.getId());
        topics.put("convention", conventions.isEmpty()
                ? "Vous n'avez pas encore de convention de stage. Créez-la dans « PFE › Convention de stage » puis soumettez-la au chef de département."
                : "Votre convention avec " + conventions.get(0).getCompanyName() + " est au statut " + conventions.get(0).getStatus() + ".");
    }

    private void teacher(User me, LocalDate today, List<String> facts, Map<String, String> topics) {
        List<PfeProject> projects = projectRepository.findBySupervisorIdOrderByCreatedAtDesc(me.getId());
        long toReview = milestoneRepository.countByProjectSupervisorIdAndStatus(me.getId(), MilestoneStatus.SUBMITTED);
        long mySubjects = subjectRepository.findBySupervisorId(me.getId()).size();
        facts.add("Projets encadrés : " + projects.size() + " ; livrables à relire : " + toReview + " ; sujets proposés : " + mySubjects);
        topics.put("jalon", toReview == 0 ? "Aucun livrable n'attend votre relecture."
                : toReview + " livrable(s) attendent votre relecture dans « PFE › Projets ».");
        topics.put("sujet", "Vous avez " + mySubjects + " sujet(s). Proposez-en un dans « PFE › Sujets › Proposer » : "
                + "l'assistant IA peut améliorer la description et extraire les mots-clés.");
        topics.put("candidature", "Les candidatures à vos sujets sont visibles sur la page de chaque sujet : acceptez ou refusez-les.");
        List<PfeDefense> juries = defenseRepository.findByJuryMember(me.getId()).stream()
                .filter(d -> d.getStatus() == DefenseStatus.SCHEDULED && !d.getDefenseDate().isBefore(today)).toList();
        StringBuilder sb = new StringBuilder(juries.isEmpty() ? "Vous n'êtes membre d'aucun jury à venir."
                : "Vous êtes membre de " + juries.size() + " jury(s) :");
        juries.forEach(d -> sb.append("\n• ").append(d.getDefenseDate().format(DATE)).append(" à ").append(d.getStartTime())
                .append(", salle ").append(d.getRoom()).append(" : « ").append(d.getProject().getSubject().getTitle()).append(" »"));
        facts.add(sb.toString());
        topics.put("soutenance", sb.toString());
        topics.put("convention", "Les conventions de stage sont validées par le chef de département.");
    }

    private void manager(User me, LocalDate today, List<String> facts, Map<String, String> topics) {
        long toValidate = subjectRepository.countByStatus(SubjectStatus.PROPOSED);
        long pendingApps = applicationRepository.countByStatus(ApplicationStatus.PENDING);
        long ready = projectRepository.countByStatus(ProjectStatus.READY_FOR_DEFENSE);
        long conventions = conventionRepository.countByStatus(ConventionStatus.SUBMITTED);
        long scheduled = defenseRepository.countByStatus(DefenseStatus.SCHEDULED);
        facts.add("Sujets à valider : " + toValidate + " ; candidatures en attente : " + pendingApps
                + " ; projets prêts à soutenir : " + ready + " ; soutenances programmées : " + scheduled
                + " ; conventions à examiner : " + conventions);
        topics.put("sujet", toValidate + " sujet(s) attendent votre validation (filtre « Proposés » dans « PFE › Sujets »).");
        topics.put("candidature", pendingApps + " candidature(s) en attente. Le bouton « Affectation automatique » "
                + "affecte les étudiants selon leurs choix (rang 1, puis 2, puis 3).");
        topics.put("soutenance", ready + " projet(s) prêt(s) à soutenir et " + scheduled + " soutenance(s) programmée(s). "
                + "Lors de la programmation, l'IA suggère un jury selon les spécialités et vérifie les conflits.");
        topics.put("jalon", "Le tableau de bord PFE indique les jalons en retard et les livrables rendus.");
        topics.put("convention", conventions + " convention(s) de stage attendent votre validation.");
    }
}
