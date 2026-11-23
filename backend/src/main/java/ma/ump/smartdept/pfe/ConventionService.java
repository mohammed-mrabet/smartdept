package ma.ump.smartdept.pfe;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.pfe.PfeDtos.ConventionRequest;
import ma.ump.smartdept.pfe.PfeDtos.ConventionResponse;
import ma.ump.smartdept.pfe.PfeDtos.ReviewConventionRequest;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;

import static ma.ump.smartdept.pfe.PfeRules.*;

/** Conventions de stage : brouillon (étudiant) → soumise → validée ou refusée (chef). */
@Service
@Transactional
public class ConventionService {

    static final int MIN_WEEKS = 4;
    static final int MAX_WEEKS = 26;

    private final InternshipConventionRepository conventionRepository;
    private final PfeProjectRepository projectRepository;
    private final NotificationService notifications;

    public ConventionService(InternshipConventionRepository conventionRepository, PfeProjectRepository projectRepository,
                             NotificationService notifications) {
        this.conventionRepository = conventionRepository;
        this.projectRepository = projectRepository;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<ConventionResponse> list(User me, ConventionStatus status) {
        List<InternshipConvention> list;
        if (me.getRole() == Role.ETUDIANT) {
            list = conventionRepository.findByStudentIdOrderByCreatedAtDesc(me.getId());
        } else {
            requireManager(me);
            list = status == null ? conventionRepository.findAllByOrderByCreatedAtDesc()
                    : conventionRepository.findByStatusOrderByCreatedAtAsc(status);
        }
        return list.stream().map(ConventionResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public ConventionResponse get(User me, Long id) {
        InternshipConvention c = find(id);
        if (!isManager(me) && !c.getStudent().getId().equals(me.getId())) {
            throw ApiException.forbidden("Vous n'avez pas accès à cette convention");
        }
        return ConventionResponse.of(c);
    }

    public ConventionResponse create(User me, ConventionRequest r) {
        if (me.getRole() != Role.ETUDIANT) {
            throw ApiException.forbidden("La convention est préparée par l'étudiant");
        }
        InternshipConvention c = new InternshipConvention();
        c.setStudent(me);
        projectRepository.findByStudentId(me.getId()).ifPresent(c::setProject);
        apply(c, r);
        return ConventionResponse.of(conventionRepository.save(c));
    }

    public ConventionResponse update(User me, Long id, ConventionRequest r) {
        InternshipConvention c = ownedEditable(me, id);
        apply(c, r);
        if (c.getStatus() == ConventionStatus.REJECTED) {
            c.setStatus(ConventionStatus.DRAFT);
        }
        return ConventionResponse.of(conventionRepository.save(c));
    }

    public ConventionResponse submit(User me, Long id) {
        InternshipConvention c = ownedEditable(me, id);
        c.setStatus(ConventionStatus.SUBMITTED);
        c.setReviewComment(null);
        InternshipConvention saved = conventionRepository.save(c);
        notifications.notifyManagers(NotificationType.CONVENTION_SUBMITTED, "Convention de stage à valider",
                me.getFullName() + " a soumis sa convention de stage chez " + c.getCompanyName() + ".",
                "/pfe/conventions/" + saved.getId());
        return ConventionResponse.of(saved);
    }

    public ConventionResponse review(User me, Long id, ReviewConventionRequest r) {
        requireManager(me);
        InternshipConvention c = find(id);
        if (c.getStatus() != ConventionStatus.SUBMITTED) {
            throw ApiException.conflict("Seule une convention soumise peut être examinée");
        }
        if (!r.approve() && clean(r.comment()) == null) {
            throw ApiException.badRequest("Indiquez le motif du refus");
        }
        c.setStatus(r.approve() ? ConventionStatus.VALIDATED : ConventionStatus.REJECTED);
        c.setReviewComment(clean(r.comment()));
        InternshipConvention saved = conventionRepository.save(c);
        notifications.notify(c.getStudent(), NotificationType.CONVENTION_REVIEWED,
                r.approve() ? "Convention de stage validée" : "Convention de stage à corriger",
                r.approve() ? "Votre convention avec " + c.getCompanyName() + " est validée : vous pouvez l'imprimer et la faire signer."
                        : "Votre convention avec " + c.getCompanyName() + " est refusée : " + c.getReviewComment(),
                "/pfe/conventions/" + saved.getId());
        return ConventionResponse.of(saved);
    }

    public void delete(User me, Long id) {
        InternshipConvention c = find(id);
        if (!c.getStudent().getId().equals(me.getId()) || c.getStatus() != ConventionStatus.DRAFT) {
            throw ApiException.conflict("Seul un brouillon peut être supprimé, par son auteur");
        }
        conventionRepository.delete(c);
    }

    private void apply(InternshipConvention c, ConventionRequest r) {
        if (!r.endDate().isAfter(r.startDate())) {
            throw ApiException.badRequest("La date de fin doit suivre la date de début");
        }
        long weeks = ChronoUnit.DAYS.between(r.startDate(), r.endDate()) / 7;
        if (weeks < MIN_WEEKS || weeks > MAX_WEEKS) {
            throw ApiException.badRequest("Un stage de PFE dure entre " + MIN_WEEKS + " et " + MAX_WEEKS + " semaines (ici : " + weeks + ")");
        }
        c.setCompanyName(r.companyName().trim());
        c.setCompanyAddress(r.companyAddress().trim());
        c.setCompanyEmail(clean(r.companyEmail()));
        c.setTutorName(r.tutorName().trim());
        c.setTutorEmail(r.tutorEmail().trim());
        c.setStartDate(r.startDate());
        c.setEndDate(r.endDate());
        c.setMissionDescription(r.missionDescription().trim());
    }

    private InternshipConvention ownedEditable(User me, Long id) {
        InternshipConvention c = find(id);
        if (!c.getStudent().getId().equals(me.getId())) {
            throw ApiException.forbidden("Cette convention ne vous appartient pas");
        }
        if (c.getStatus() != ConventionStatus.DRAFT && c.getStatus() != ConventionStatus.REJECTED) {
            throw ApiException.conflict("La convention est déjà soumise ou validée");
        }
        return c;
    }

    private InternshipConvention find(Long id) {
        return conventionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Convention introuvable : " + id));
    }
}
