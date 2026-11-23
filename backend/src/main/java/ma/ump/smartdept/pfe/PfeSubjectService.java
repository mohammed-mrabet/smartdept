package ma.ump.smartdept.pfe;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.pfe.PfeDtos.RejectRequest;
import ma.ump.smartdept.pfe.PfeDtos.SubjectRequest;
import ma.ump.smartdept.pfe.PfeDtos.SubjectResponse;
import ma.ump.smartdept.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static ma.ump.smartdept.pfe.PfeRules.*;

/**
 * Sujets de PFE.
 * Cycle : PROPOSED (enseignant) → PUBLISHED (validé par le chef) → ASSIGNED (complet) ; REJECTED avec motif ; ARCHIVED.
 * Visibilité : un étudiant ne voit que les sujets publiés de sa filière ; un enseignant voit les sujets publiés
 * et tous les siens ; le chef et l'administrateur voient tout.
 */
@Service
@Transactional
public class PfeSubjectService {

    private final PfeSubjectRepository subjectRepository;
    private final PfeApplicationRepository applicationRepository;
    private final PfeProjectRepository projectRepository;
    private final FiliereRepository filiereRepository;
    private final UserRepository userRepository;
    private final NotificationService notifications;
    private final Clock clock;

    public PfeSubjectService(PfeSubjectRepository subjectRepository, PfeApplicationRepository applicationRepository,
                             PfeProjectRepository projectRepository, FiliereRepository filiereRepository,
                             UserRepository userRepository, NotificationService notifications, Clock clock) {
        this.subjectRepository = subjectRepository;
        this.applicationRepository = applicationRepository;
        this.projectRepository = projectRepository;
        this.filiereRepository = filiereRepository;
        this.userRepository = userRepository;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------- consultation ----------

    @Transactional(readOnly = true)
    public List<SubjectResponse> search(User me, SubjectStatus status, Long filiereId, Long supervisorId,
                                        SubjectType type, String q, boolean mine) {
        String query = clean(q);
        if (me.getRole() == Role.ETUDIANT) {
            Long studentFiliere = me.getFiliere() == null ? -1L : me.getFiliere().getId();
            return subjectRepository.search(SubjectStatus.PUBLISHED, studentFiliere, supervisorId, type, query).stream()
                    .map(this::toResponse).toList();
        }
        Long supervisor = mine ? me.getId() : supervisorId;
        return subjectRepository.search(status, filiereId, supervisor, type, query).stream()
                .filter(s -> canSee(me, s))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SubjectResponse get(User me, Long id) {
        PfeSubject s = find(id);
        if (!canSee(me, s)) {
            throw ApiException.forbidden("Vous n'avez pas accès à ce sujet");
        }
        return toResponse(s);
    }

    // ---------- proposition et modification ----------

    public SubjectResponse create(User me, SubjectRequest r) {
        if (!isTeacher(me) && !isManager(me)) {
            throw ApiException.forbidden("Seuls les enseignants et le chef de département proposent des sujets");
        }
        PfeSubject s = new PfeSubject();
        User supervisor = resolveSupervisor(me, r.supervisorId());
        apply(s, r, supervisor);
        boolean autoPublish = isManager(me);
        s.setStatus(autoPublish ? SubjectStatus.PUBLISHED : SubjectStatus.PROPOSED);
        PfeSubject saved = subjectRepository.save(s);
        if (autoPublish) {
            announcePublication(saved);
        } else {
            notifications.notifyManagers(NotificationType.SUBJECT_PROPOSED, "Nouveau sujet à valider",
                    me.getFullName() + " propose le sujet « " + saved.getTitle() + " » (" + saved.getFiliere().getCode() + ").",
                    "/pfe/sujets/" + saved.getId());
        }
        return toResponse(saved);
    }

    public SubjectResponse update(User me, Long id, SubjectRequest r) {
        PfeSubject s = find(id);
        boolean owner = s.getSupervisor().getId().equals(me.getId());
        if (isManager(me)) {
            if (s.getStatus() == SubjectStatus.ASSIGNED || s.getStatus() == SubjectStatus.ARCHIVED) {
                throw ApiException.conflict("Un sujet affecté ou archivé ne peut plus être modifié");
            }
        } else if (!owner) {
            throw ApiException.forbidden("Seul l'encadrant du sujet peut le modifier");
        } else if (s.getStatus() != SubjectStatus.PROPOSED && s.getStatus() != SubjectStatus.REJECTED) {
            throw ApiException.conflict("Le sujet est déjà publié : demandez au chef de département de le modifier");
        }
        User supervisor = isManager(me) && r.supervisorId() != null ? resolveSupervisor(me, r.supervisorId()) : s.getSupervisor();
        if (s.getMaxStudents() > r.maxStudents() && countAccepted(s) > r.maxStudents()) {
            throw ApiException.conflict("Des étudiants sont déjà affectés : la capacité ne peut pas descendre à " + r.maxStudents());
        }
        apply(s, r, supervisor);
        if (s.getStatus() == SubjectStatus.REJECTED && owner && !isManager(me)) {
            s.setStatus(SubjectStatus.PROPOSED);
            s.setRejectionReason(null);
            notifications.notifyManagers(NotificationType.SUBJECT_PROPOSED, "Sujet corrigé à valider",
                    me.getFullName() + " a corrigé le sujet « " + s.getTitle() + " ».", "/pfe/sujets/" + s.getId());
        }
        return toResponse(subjectRepository.save(s));
    }

    public void delete(User me, Long id) {
        PfeSubject s = find(id);
        requireSupervisorOrManager(me, s.getSupervisor());
        if (s.getStatus() != SubjectStatus.PROPOSED && s.getStatus() != SubjectStatus.REJECTED) {
            throw ApiException.conflict("Seul un sujet non publié peut être supprimé : archivez-le plutôt");
        }
        subjectRepository.delete(s);
    }

    // ---------- validation par le chef ----------

    public SubjectResponse publish(User me, Long id) {
        requireManager(me);
        PfeSubject s = find(id);
        if (s.getStatus() != SubjectStatus.PROPOSED && s.getStatus() != SubjectStatus.REJECTED) {
            throw ApiException.conflict("Seul un sujet proposé ou refusé peut être publié (statut actuel : " + s.getStatus() + ")");
        }
        s.setStatus(SubjectStatus.PUBLISHED);
        s.setRejectionReason(null);
        PfeSubject saved = subjectRepository.save(s);
        notifications.notify(saved.getSupervisor(), NotificationType.SUBJECT_PUBLISHED, "Votre sujet est publié",
                "Le sujet « " + saved.getTitle() + " » a été validé : les étudiants de " + saved.getFiliere().getCode()
                        + " peuvent maintenant candidater.", "/pfe/sujets/" + saved.getId());
        announcePublication(saved);
        return toResponse(saved);
    }

    public SubjectResponse reject(User me, Long id, RejectRequest r) {
        requireManager(me);
        PfeSubject s = find(id);
        if (s.getStatus() != SubjectStatus.PROPOSED) {
            throw ApiException.conflict("Seul un sujet en attente de validation peut être refusé");
        }
        s.setStatus(SubjectStatus.REJECTED);
        s.setRejectionReason(r.reason().trim());
        PfeSubject saved = subjectRepository.save(s);
        notifications.notify(saved.getSupervisor(), NotificationType.SUBJECT_REJECTED, "Sujet à revoir",
                "Le sujet « " + saved.getTitle() + " » n'a pas été validé. Motif : " + saved.getRejectionReason()
                        + ". Vous pouvez le corriger et le soumettre de nouveau.", "/pfe/sujets/" + saved.getId());
        return toResponse(saved);
    }

    public SubjectResponse archive(User me, Long id) {
        PfeSubject s = find(id);
        requireSupervisorOrManager(me, s.getSupervisor());
        if (projectRepository.findBySubjectId(s.getId()).isPresent()) {
            throw ApiException.conflict("Des étudiants sont affectés à ce sujet : il ne peut pas être archivé");
        }
        s.setStatus(SubjectStatus.ARCHIVED);
        for (PfeApplication a : applicationRepository.findBySubjectIdAndStatus(s.getId(), ApplicationStatus.PENDING)) {
            a.setStatus(ApplicationStatus.WITHDRAWN);
            a.setDecisionComment("Sujet retiré par l'encadrant");
            notifications.notify(a.getStudent(), NotificationType.APPLICATION_REJECTED, "Sujet retiré",
                    "Le sujet « " + s.getTitle() + " » a été retiré : votre candidature est annulée. Pensez à choisir un autre sujet.",
                    "/pfe/sujets");
        }
        return toResponse(subjectRepository.save(s));
    }

    // ---------- règles ----------

    private void apply(PfeSubject s, SubjectRequest r, User supervisor) {
        if (r.type() == SubjectType.ENTREPRISE && clean(r.companyName()) == null) {
            throw ApiException.badRequest("Un sujet en entreprise doit indiquer le nom de l'entreprise");
        }
        Filiere filiere = filiereRepository.findById(r.filiereId())
                .orElseThrow(() -> ApiException.notFound("Filière introuvable : " + r.filiereId()));
        s.setTitle(r.title().trim());
        s.setDescription(r.description().trim());
        s.setKeywords(normalizeKeywords(r.keywords()));
        s.setType(r.type());
        s.setCompanyName(clean(r.companyName()));
        s.setMaxStudents(r.maxStudents());
        s.setFiliere(filiere);
        s.setSupervisor(supervisor);
        s.setAcademicYear(r.academicYear() != null ? r.academicYear() : currentAcademicYear(LocalDate.now(clock)));
    }

    private User resolveSupervisor(User me, Long supervisorId) {
        if (!isManager(me) || supervisorId == null) {
            if (isManager(me) && !isTeacher(me)) {
                throw ApiException.badRequest("Indiquez l'enseignant encadrant (supervisorId)");
            }
            return me;
        }
        User u = userRepository.findById(supervisorId)
                .orElseThrow(() -> ApiException.notFound("Encadrant introuvable : " + supervisorId));
        if (!isTeacher(u)) {
            throw ApiException.badRequest(u.getFullName() + " n'est pas enseignant : il ne peut pas encadrer un PFE");
        }
        return u;
    }

    /** "Spring Boot, angular , , Docker" → "Spring Boot, Angular, Docker" (sans doublon). */
    static String normalizeKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        LinkedHashSet<String> unique = Arrays.stream(raw.split("[,;]"))
                .map(String::trim).filter(k -> !k.isEmpty())
                .map(k -> Character.toUpperCase(k.charAt(0)) + k.substring(1))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String joined = String.join(", ", unique);
        return joined.length() > 500 ? joined.substring(0, 500) : joined;
    }

    private void announcePublication(PfeSubject s) {
        List<User> students = userRepository.findByRoleAndFiliereIdAndEnabledTrue(Role.ETUDIANT, s.getFiliere().getId());
        notifications.notifyAll(students, NotificationType.SUBJECT_PUBLISHED, "Nouveau sujet de PFE",
                "Nouveau sujet publié par " + s.getSupervisor().getFullName() + " : « " + s.getTitle() + " ».",
                "/pfe/sujets/" + s.getId());
    }

    boolean canSee(User me, PfeSubject s) {
        if (isManager(me) || s.getSupervisor().getId().equals(me.getId())) {
            return true;
        }
        if (me.getRole() == Role.ETUDIANT) {
            boolean sameFiliere = me.getFiliere() != null && me.getFiliere().getId().equals(s.getFiliere().getId());
            return sameFiliere && (s.getStatus() == SubjectStatus.PUBLISHED || s.getStatus() == SubjectStatus.ASSIGNED);
        }
        return s.getStatus() == SubjectStatus.PUBLISHED || s.getStatus() == SubjectStatus.ASSIGNED;
    }

    private long countAccepted(PfeSubject s) {
        return applicationRepository.countBySubjectIdAndStatus(s.getId(), ApplicationStatus.ACCEPTED);
    }

    PfeSubject find(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Sujet introuvable : " + id));
    }

    SubjectResponse toResponse(PfeSubject s) {
        return SubjectResponse.of(s, applicationRepository.countBySubjectIdAndStatus(s.getId(), ApplicationStatus.PENDING),
                countAccepted(s));
    }
}
