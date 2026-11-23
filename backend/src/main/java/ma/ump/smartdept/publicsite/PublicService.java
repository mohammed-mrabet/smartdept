package ma.ump.smartdept.publicsite;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.common.RateLimiter;
import ma.ump.smartdept.config.CacheConfig;
import ma.ump.smartdept.notification.EmailStatus;
import ma.ump.smartdept.notification.MailRouter;
import ma.ump.smartdept.notification.MailTemplates;
import ma.ump.smartdept.notification.NotificationRepository;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.offers.InternshipOfferRepository;
import ma.ump.smartdept.offers.OfferDtos;
import ma.ump.smartdept.offers.OfferInsights;
import ma.ump.smartdept.pfe.DefenseStatus;
import ma.ump.smartdept.pfe.PfeDefenseRepository;
import ma.ump.smartdept.pfe.PfeSubjectRepository;
import ma.ump.smartdept.pfe.SubjectStatus;
import ma.ump.smartdept.publicsite.PublicDtos.*;
import ma.ump.smartdept.user.FiliereRepository;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Données de la page d'accueil publique (mises en cache) et formulaire de contact. */
@Service
@Transactional(readOnly = true)
public class PublicService {

    private final UserRepository userRepository;
    private final FiliereRepository filiereRepository;
    private final PfeSubjectRepository subjectRepository;
    private final PfeDefenseRepository defenseRepository;
    private final InternshipOfferRepository offerRepository;
    private final NotificationRepository notificationRepository;
    private final OfferInsights insights;
    private final ContactMessageRepository contactRepository;
    private final NotificationService notifications;
    private final MailRouter mailRouter;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public PublicService(UserRepository userRepository, FiliereRepository filiereRepository, PfeSubjectRepository subjectRepository,
                         PfeDefenseRepository defenseRepository, InternshipOfferRepository offerRepository,
                         NotificationRepository notificationRepository, OfferInsights insights, ContactMessageRepository contactRepository,
                         NotificationService notifications, MailRouter mailRouter, RateLimiter rateLimiter, Clock clock) {
        this.userRepository = userRepository;
        this.filiereRepository = filiereRepository;
        this.subjectRepository = subjectRepository;
        this.defenseRepository = defenseRepository;
        this.offerRepository = offerRepository;
        this.notificationRepository = notificationRepository;
        this.insights = insights;
        this.contactRepository = contactRepository;
        this.notifications = notifications;
        this.mailRouter = mailRouter;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Cacheable(CacheConfig.PUBLIC_STATS)
    public PublicStats stats() {
        OfferDtos.Facets f = insights.facets();
        long countries = f.countries().keySet().stream().filter(c -> !c.equals("OTHER") && !c.equals("REMOTE")).count();
        return new PublicStats(userRepository.countByRoleAndEnabledTrue(Role.ETUDIANT),
                userRepository.countByRoleAndEnabledTrue(Role.ENSEIGNANT) + userRepository.countByRoleAndEnabledTrue(Role.CHEF_DEPARTEMENT),
                filiereRepository.count(), subjectRepository.countByStatus(SubjectStatus.PUBLISHED), offerRepository.countByActiveTrue(),
                countries, defenseRepository.countByStatus(DefenseStatus.SCHEDULED), notificationRepository.countByEmailStatus(EmailStatus.SENT),
                f.countries(), f.levels());
    }

    /** Dernières offres, sans lien ni contact : la fiche complète est réservée aux membres connectés. */
    @Cacheable(CacheConfig.PUBLIC_OFFERS)
    public List<PublicOffer> latestOffers() {
        return offerRepository.findTop300ByActiveTrueOrderByPublishedAtDesc().stream().limit(9)
                .map(o -> new PublicOffer(o.getId(), o.getTitle(), o.getCompany(), o.getLocation(), o.getCountry().getFlag(),
                        o.getCountry().getLabel(), o.getKind().getLabel(), OfferDtos.levelLabel(o.getLevelMin(), o.getLevelMax()),
                        o.isRemote(), OfferDtos.skillList(o.getSkills()), o.getPublishedAt(),
                        o.isInternal() ? "Département" : o.getSource().getLabel()))
                .toList();
    }

    @Transactional
    public void contact(ContactRequest r, String clientIp) {
        if (r.website() != null && !r.website().isBlank()) throw ApiException.badRequest("Message refusé");
        rateLimiter.check("contact:" + clientIp, 5, Duration.ofHours(1), "Trop de messages envoyés : réessayez dans une heure");
        ContactMessage m = new ContactMessage();
        m.setName(r.name().trim());
        m.setEmail(r.email().trim());
        m.setOrganisation(r.organisation() == null || r.organisation().isBlank() ? null : r.organisation().trim());
        m.setTopic(r.topic());
        m.setMessage(r.message().trim());
        contactRepository.save(m);
        notifications.notifyManagers(NotificationType.CONTACT_MESSAGE, "Nouveau message : " + label(r.topic()),
                m.getName() + (m.getOrganisation() == null ? "" : " (" + m.getOrganisation() + ")") + " : "
                        + (m.getMessage().length() > 160 ? m.getMessage().substring(0, 160) + "…" : m.getMessage()), "/admin/messagerie");
        String ack = "Nous avons bien reçu votre message (« " + label(r.topic()) + " »). Le département informatique vous répondra rapidement.\n\n"
                + "Votre message :\n" + m.getMessage();
        mailRouter.send(m.getEmail(), "[SmartDept] Message reçu", MailTemplates.text(m.getName(), ack, null),
                MailTemplates.html(m.getName(), "Message bien reçu", ack, null, null, "Accusé de réception automatique."));
    }

    public List<ContactResponse> messages() {
        return contactRepository.findTop100ByOrderByHandledAscCreatedAtDesc().stream().map(ContactResponse::of).toList();
    }

    @Transactional
    public ContactResponse markHandled(Long id, boolean handled) {
        ContactMessage m = contactRepository.findById(id).orElseThrow(() -> ApiException.notFound("Message introuvable"));
        m.setHandled(handled);
        m.setHandledAt(handled ? LocalDateTime.now(clock) : null);
        return ContactResponse.of(m);
    }

    static String label(ContactMessage.Topic t) {
        return switch (t) {
            case PARTENARIAT -> "partenariat entreprise";
            case OFFRE_STAGE -> "proposer une offre de stage";
            case INSCRIPTION -> "question sur l'inscription";
            default -> "autre demande";
        };
    }
}
