package ma.ump.smartdept.offers;

import ma.ump.smartdept.ai.TextAnalyzer;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Alertes : quand de nouvelles offres arrivent, chaque recherche enregistrée qui leur correspond
 * déclenche UNE notification groupée (dans l'application et par e-mail).
 * Chaque matin, rappel de relance pour les candidatures envoyées depuis 14 jours sans réponse.
 */
@Service
public class OfferAlertService {

    static final int FOLLOW_UP_DAYS = 14;

    private final OfferAlertRepository alertRepository;
    private final InternshipOfferRepository offerRepository;
    private final OfferBookmarkRepository bookmarkRepository;
    private final NotificationService notifications;
    private final Clock clock;

    public OfferAlertService(OfferAlertRepository alertRepository, InternshipOfferRepository offerRepository,
                             OfferBookmarkRepository bookmarkRepository, NotificationService notifications, Clock clock) {
        this.alertRepository = alertRepository;
        this.offerRepository = offerRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Renvoie le nombre d'alertes déclenchées. */
    @Transactional
    public int dispatch(Collection<Long> newOfferIds) {
        if (newOfferIds == null || newOfferIds.isEmpty()) return 0;
        List<InternshipOffer> offers = offerRepository.findAllById(newOfferIds);
        int sent = 0;
        for (OfferAlert alert : alertRepository.findActiveWithUser()) {
            List<InternshipOffer> matched = offers.stream().filter(o -> matches(alert, o)).toList();
            if (matched.isEmpty()) continue;
            String preview = matched.stream().limit(3)
                    .map(o -> "• " + o.getTitle() + (o.getCompany() == null ? "" : " — " + o.getCompany())
                            + " (" + o.getCountry().getLabel() + ")")
                    .collect(Collectors.joining("\n"));
            String more = matched.size() > 3 ? "\n… et " + (matched.size() - 3) + " autre(s)." : "";
            notifications.notify(alert.getUser(), NotificationType.OFFER_ALERT,
                    matched.size() + " nouvelle(s) offre(s) pour « " + alert.getName() + " »",
                    preview + more, "/offres?alerte=" + alert.getId());
            alert.setLastNotifiedAt(LocalDateTime.now(clock));
            alert.setMatchesSent(alert.getMatchesSent() + matched.size());
            sent++;
        }
        return sent;
    }

    /** Tous les critères renseignés doivent être satisfaits ; chaque mot-clé doit apparaître dans l'offre. */
    public static boolean matches(OfferAlert a, InternshipOffer o) {
        if (!o.isActive()) return false;
        if (a.getCountry() != null && a.getCountry() != o.getCountry()) return false;
        if (a.getKind() != null && a.getKind() != o.getKind()) return false;
        if (a.isRemoteOnly() && !o.isRemote()) return false;
        if (a.getStudyLevel() != null && !o.acceptsLevel(a.getStudyLevel())) return false;
        if (a.getKeywords() == null || a.getKeywords().isBlank()) return true;
        String haystack = TextAnalyzer.normalize(String.join(" ", o.getTitle(), String.valueOf(o.getCompany()),
                String.valueOf(o.getSkills()), String.valueOf(o.getLocation()), String.valueOf(o.getDescription())));
        for (String word : TextAnalyzer.normalize(a.getKeywords()).split("[,;\\s]+")) {
            if (!word.isBlank() && !haystack.contains(word)) return false;
        }
        return true;
    }

    @Scheduled(cron = "${app.offers.follow-up-cron:0 0 9 * * *}")
    @Transactional
    public int sendFollowUps() {
        LocalDate limit = LocalDate.now(clock).minusDays(FOLLOW_UP_DAYS);
        int sent = 0;
        for (OfferBookmark b : bookmarkRepository.findDueForFollowUp(limit)) {
            InternshipOffer o = b.getOffer();
            notifications.notify(b.getUser(), NotificationType.OFFER_FOLLOW_UP, "Pensez à relancer " + (o.getCompany() == null ? "l'entreprise" : o.getCompany()),
                    "Vous avez postulé le " + b.getAppliedAt() + " à « " + o.getTitle() + " » sans réponse depuis "
                            + FOLLOW_UP_DAYS + " jours. Un court e-mail de relance augmente vos chances.", "/offres/suivi");
            b.setFollowUpSent(true);
            sent++;
        }
        return sent;
    }
}
