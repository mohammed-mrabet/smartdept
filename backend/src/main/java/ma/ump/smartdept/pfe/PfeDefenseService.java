package ma.ump.smartdept.pfe;

import ma.ump.smartdept.ai.AiService;
import ma.ump.smartdept.ai.TextAnalyzer;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static ma.ump.smartdept.pfe.PfeRules.*;

/**
 * Soutenances : programmation par le chef (salle, jury de 2 à 5 enseignants dont un président),
 * avec détection des conflits de salle et de jury, puis saisie de la note et de la mention.
 */
@Service
@Transactional
public class PfeDefenseService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PfeDefenseRepository defenseRepository;
    private final PfeProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationService notifications;
    private final Clock clock;

    public PfeDefenseService(PfeDefenseRepository defenseRepository, PfeProjectRepository projectRepository,
                             UserRepository userRepository, NotificationService notifications, Clock clock) {
        this.defenseRepository = defenseRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------- consultation ----------

    @Transactional(readOnly = true)
    public List<DefenseResponse> list(User me, LocalDate from, LocalDate to, boolean mine) {
        LocalDate start = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate end = to != null ? to : LocalDate.of(2100, 12, 31);
        if (end.isBefore(start)) {
            throw ApiException.badRequest("La date de fin doit suivre la date de début");
        }
        return defenseRepository.findBetween(start, end).stream()
                .filter(d -> canSee(me, d, mine))
                .map(DefenseResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public DefenseResponse get(User me, Long id) {
        PfeDefense d = find(id);
        if (!canSee(me, d, false)) {
            throw ApiException.forbidden("Vous n'avez pas accès à cette soutenance");
        }
        return DefenseResponse.of(d);
    }

    // ---------- programmation ----------

    public DefenseResponse schedule(User me, DefenseRequest r) {
        requireManager(me);
        PfeProject project = projectRepository.findById(r.projectId())
                .orElseThrow(() -> ApiException.notFound("Projet introuvable : " + r.projectId()));
        if (project.getStatus() != ProjectStatus.READY_FOR_DEFENSE) {
            throw ApiException.conflict("Le projet doit être déclaré prêt par l'encadrant avant de programmer la soutenance");
        }
        PfeDefense d = defenseRepository.findByProjectId(project.getId()).orElse(null);
        if (d != null && d.getStatus() != DefenseStatus.CANCELLED) {
            throw ApiException.conflict("Une soutenance est déjà programmée pour ce projet : modifiez-la");
        }
        if (d == null) {
            d = new PfeDefense();
            d.setProject(project);
        }
        apply(d, r, d.getId());
        d.setStatus(DefenseStatus.SCHEDULED);
        PfeDefense saved = defenseRepository.save(d);
        notifyParticipants(saved, NotificationType.DEFENSE_SCHEDULED, "Soutenance programmée");
        return DefenseResponse.of(saved);
    }

    public DefenseResponse update(User me, Long id, DefenseRequest r) {
        requireManager(me);
        PfeDefense d = find(id);
        if (d.getStatus() != DefenseStatus.SCHEDULED) {
            throw ApiException.conflict("Seule une soutenance programmée peut être modifiée");
        }
        if (!d.getProject().getId().equals(r.projectId())) {
            throw ApiException.badRequest("Le projet d'une soutenance ne peut pas être changé");
        }
        apply(d, r, d.getId());
        PfeDefense saved = defenseRepository.save(d);
        notifyParticipants(saved, NotificationType.DEFENSE_UPDATED, "Soutenance modifiée");
        return DefenseResponse.of(saved);
    }

    public DefenseResponse cancel(User me, Long id) {
        requireManager(me);
        PfeDefense d = find(id);
        if (d.getStatus() != DefenseStatus.SCHEDULED) {
            throw ApiException.conflict("Seule une soutenance programmée peut être annulée");
        }
        d.setStatus(DefenseStatus.CANCELLED);
        PfeDefense saved = defenseRepository.save(d);
        List<User> people = participants(saved);
        notifications.notifyAll(people, NotificationType.DEFENSE_UPDATED, "Soutenance annulée",
                "La soutenance du projet « " + saved.getProject().getSubject().getTitle() + " » prévue le "
                        + saved.getDefenseDate().format(DATE) + " est annulée. Une nouvelle date sera communiquée.",
                "/pfe/soutenances");
        return DefenseResponse.of(saved);
    }

    /** Note finale : saisie par le président du jury ou par le chef. */
    public DefenseResponse grade(User me, Long id, GradeRequest r) {
        PfeDefense d = find(id);
        if (!isManager(me) && !d.getPresident().getId().equals(me.getId())) {
            throw ApiException.forbidden("Seul le président du jury ou le chef de département saisit la note");
        }
        if (d.getStatus() != DefenseStatus.SCHEDULED) {
            throw ApiException.conflict("La soutenance n'est pas au statut programmé");
        }
        if (d.getDefenseDate().isAfter(LocalDate.now(clock))) {
            throw ApiException.conflict("La note ne peut être saisie qu'à partir du jour de la soutenance");
        }
        d.setGrade(Math.round(r.grade() * 4) / 4.0);
        d.setMention(PfeDefense.mentionFor(d.getGrade()));
        d.setJuryComment(clean(r.comment()));
        d.setStatus(DefenseStatus.DONE);
        PfeProject p = d.getProject();
        p.setStatus(ProjectStatus.DEFENDED);
        projectRepository.save(p);
        PfeDefense saved = defenseRepository.save(d);
        List<User> people = new ArrayList<>(p.getStudents());
        people.add(p.getSupervisor());
        notifications.notifyAll(people, NotificationType.DEFENSE_GRADED, "Résultat de la soutenance",
                "Soutenance du projet « " + p.getSubject().getTitle() + " » : note " + saved.getGrade() + "/20, mention "
                        + saved.getMention() + ".", "/pfe/mon-projet");
        return DefenseResponse.of(saved);
    }

    // ---------- jury ----------

    /**
     * Membres de jury proposés : proximité entre le sujet et les spécialités de chaque enseignant,
     * disponibilité sur le créneau (si fourni) et nombre de jurys déjà prévus (équilibrage de la charge).
     */
    @Transactional(readOnly = true)
    public List<JurySuggestion> suggestJury(User me, Long projectId, LocalDate date, LocalTime start, LocalTime end) {
        requireManager(me);
        PfeProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Projet introuvable : " + projectId));
        String text = AiService.subjectText(project.getSubject());
        List<PfeDefense> overlapping = date != null && start != null && end != null
                ? defenseRepository.findOverlapping(date, start, end, null, DefenseStatus.SCHEDULED) : List.of();
        Set<Long> busy = new HashSet<>();
        overlapping.forEach(d -> d.getJury().forEach(j -> busy.add(j.getId())));

        List<JurySuggestion> out = new ArrayList<>();
        for (User t : userRepository.findByRoleInAndEnabledTrue(List.of(Role.ENSEIGNANT, Role.CHEF_DEPARTEMENT))) {
            boolean isSupervisor = t.getId().equals(project.getSupervisor().getId());
            String profile = Optional.ofNullable(t.getSpecialties()).orElse("");
            double coverage = TextAnalyzer.coverage(text, profile);
            long load = defenseRepository.findByJuryMember(t.getId()).stream()
                    .filter(d -> d.getStatus() == DefenseStatus.SCHEDULED).count();
            double score = Math.round((coverage - 0.05 * load + (isSupervisor ? 0.2 : 0)) * 100) / 100.0;
            List<String> matched = TextAnalyzer.commonTerms(profile, text).stream().limit(5).toList();
            String reason = isSupervisor ? "Encadrant du projet (membre, mais pas président)"
                    : matched.isEmpty() ? "Spécialités éloignées du sujet" : "Spécialités proches : " + String.join(", ", matched);
            if (load > 0) {
                reason += " ; " + load + " jury(s) déjà prévu(s)";
            }
            out.add(new JurySuggestion(UserSummary.of(t), Math.max(0, score), matched, !busy.contains(t.getId()), reason));
        }
        out.sort(Comparator.comparing(JurySuggestion::available).reversed()
                .thenComparing(Comparator.comparingDouble(JurySuggestion::score).reversed()));
        return out;
    }

    // ---------- règles ----------

    private void apply(PfeDefense d, DefenseRequest r, Long excludeId) {
        if (!r.endTime().isAfter(r.startTime())) {
            throw ApiException.badRequest("L'heure de fin doit être après l'heure de début");
        }
        if (r.defenseDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("La date de soutenance ne peut pas être dans le passé");
        }
        Set<Long> ids = new LinkedHashSet<>(r.juryIds());
        if (ids.size() < 2) {
            throw ApiException.badRequest("Le jury compte au moins 2 membres différents");
        }
        if (!ids.contains(r.presidentId())) {
            throw ApiException.badRequest("Le président doit faire partie du jury");
        }
        if (r.presidentId().equals(d.getProject().getSupervisor().getId())) {
            throw ApiException.badRequest("L'encadrant ne peut pas présider le jury de son propre étudiant");
        }
        Set<User> jury = new LinkedHashSet<>();
        for (Long id : ids) {
            User u = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Membre du jury introuvable : " + id));
            if (!isTeacher(u)) {
                throw ApiException.badRequest(u.getFullName() + " n'est pas enseignant : il ne peut pas siéger au jury");
            }
            jury.add(u);
        }
        String room = r.room().trim().toUpperCase();
        List<String> conflicts = new ArrayList<>();
        for (PfeDefense other : defenseRepository.findOverlapping(r.defenseDate(), r.startTime(), r.endTime(), excludeId, DefenseStatus.SCHEDULED)) {
            String slot = other.getStartTime() + "-" + other.getEndTime() + " (« " + other.getProject().getSubject().getTitle() + " »)";
            if (other.getRoom().equalsIgnoreCase(room)) {
                conflicts.add("la salle " + room + " est déjà prise " + slot);
            }
            for (User member : other.getJury()) {
                if (ids.contains(member.getId())) {
                    conflicts.add(member.getFullName() + " siège déjà dans un jury " + slot);
                }
            }
        }
        if (!conflicts.isEmpty()) {
            throw ApiException.conflict("Conflit le " + r.defenseDate().format(DATE) + " : " + String.join(" ; ", conflicts));
        }
        d.setDefenseDate(r.defenseDate());
        d.setStartTime(r.startTime());
        d.setEndTime(r.endTime());
        d.setRoom(room);
        d.getJury().clear();
        d.getJury().addAll(jury);
        d.setPresident(jury.stream().filter(u -> u.getId().equals(r.presidentId())).findFirst().orElseThrow());
    }

    private List<User> participants(PfeDefense d) {
        List<User> people = new ArrayList<>(d.getProject().getStudents());
        people.add(d.getProject().getSupervisor());
        people.addAll(d.getJury());
        return people;
    }

    private void notifyParticipants(PfeDefense d, NotificationType type, String title) {
        String when = d.getDefenseDate().format(DATE) + " de " + d.getStartTime() + " à " + d.getEndTime() + ", salle " + d.getRoom();
        notifications.notifyAll(d.getProject().getStudents(), type, title,
                "Votre soutenance aura lieu le " + when + ". Président du jury : " + d.getPresident().getFullName() + ".",
                "/pfe/mon-projet");
        List<User> staff = new ArrayList<>(d.getJury());
        staff.add(d.getProject().getSupervisor());
        notifications.notifyAll(staff, type, title,
                "Soutenance du projet « " + d.getProject().getSubject().getTitle() + " » le " + when + ".",
                "/pfe/soutenances");
    }

    private boolean canSee(User me, PfeDefense d, boolean mine) {
        boolean involved = d.getProject().hasMember(me)
                || d.getProject().getSupervisor().getId().equals(me.getId())
                || d.getJury().stream().anyMatch(j -> j.getId().equals(me.getId()));
        if (mine || me.getRole() == Role.ETUDIANT) {
            return involved;
        }
        return true;
    }

    private PfeDefense find(Long id) {
        return defenseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Soutenance introuvable : " + id));
    }
}
