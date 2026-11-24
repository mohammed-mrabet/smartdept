package ma.ump.smartdept.offers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Première synchronisation des offres au démarrage, en arrière-plan : sans elle, les vraies offres des API
 * n'apparaîtraient qu'au prochain passage planifié (jusqu'à 6 heures d'attente). Ignorée si une synchronisation
 * réussie a eu lieu il y a moins de 6 heures (redémarrage du conteneur), pour respecter les quotas des API.
 */
@Component
public class OfferStartupSync {

    private static final Logger log = LoggerFactory.getLogger(OfferStartupSync.class);

    private final OfferSyncService syncService;
    private final OfferSyncRunRepository runRepository;
    private final Clock clock;
    private final boolean enabled;

    public OfferStartupSync(OfferSyncService syncService, OfferSyncRunRepository runRepository, Clock clock,
                            @Value("${app.offers.sync-on-startup:true}") boolean enabled) {
        this.syncService = syncService;
        this.runRepository = runRepository;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) return;
        if (recentlySynced()) {
            log.info("Synchronisation des offres au démarrage ignorée : dernière synchronisation il y a moins de 6 heures");
            return;
        }
        try {
            OfferDtos.SyncSummary s = syncService.syncAll(null);
            log.info("Synchronisation des offres au démarrage : {} nouvelles, {} mises à jour", s.created(), s.updated());
        } catch (RuntimeException e) {
            log.warn("Synchronisation des offres au démarrage impossible : {}", e.getMessage());
        }
    }

    boolean recentlySynced() {
        LocalDateTime limit = LocalDateTime.now(clock).minusHours(6);
        return syncService.providers().stream()
                .filter(p -> p.isConfigured())
                .map(p -> runRepository.findTopBySourceAndSuccessTrueOrderByStartedAtDesc(p.source()))
                .anyMatch(r -> r.isPresent() && r.get().getFetched() > 0 && r.get().getStartedAt().isAfter(limit));
    }
}
