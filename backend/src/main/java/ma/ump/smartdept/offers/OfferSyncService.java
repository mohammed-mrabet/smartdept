package ma.ump.smartdept.offers;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.offers.OfferDtos.LiveSearchRequest;
import ma.ump.smartdept.offers.OfferDtos.SyncResult;
import ma.ump.smartdept.offers.OfferDtos.SyncSummary;
import ma.ump.smartdept.offers.provider.FetchedOffer;
import ma.ump.smartdept.offers.provider.OfferProvider;
import ma.ump.smartdept.offers.provider.OfferQuery;
import ma.ump.smartdept.user.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synchronisation des offres : toutes les 6 heures (app.offers.sync-cron) pour chaque fournisseur configuré,
 * par pays et par type d'offre ; ou à la demande (« recherche en direct »). Les échecs d'une API n'arrêtent pas les autres.
 */
@Service
public class OfferSyncService {

    private static final Logger log = LoggerFactory.getLogger(OfferSyncService.class);
    private static final List<OfferKind> KINDS = List.of(OfferKind.STAGE, OfferKind.PFE, OfferKind.ALTERNANCE, OfferKind.EMPLOI);

    private final List<OfferProvider> providers;
    private final OfferStore store;
    private final InternshipOfferRepository offerRepository;
    private final OfferSyncRunRepository runRepository;
    private final OfferAlertService alertService;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final int retentionDays;
    private final int resultsPerQuery;
    private final Map<OfferSource, Instant> lastCall = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastLiveSearch = new ConcurrentHashMap<>();
    private org.springframework.cache.CacheManager cacheManager;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCacheManager(org.springframework.cache.CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public OfferSyncService(List<OfferProvider> providers, OfferStore store, InternshipOfferRepository offerRepository,
                            OfferSyncRunRepository runRepository, OfferAlertService alertService, Clock clock, MeterRegistry metrics,
                            @Value("${app.offers.retention-days:45}") int retentionDays,
                            @Value("${app.offers.results-per-query:50}") int resultsPerQuery) {
        this.providers = providers;
        this.store = store;
        this.offerRepository = offerRepository;
        this.runRepository = runRepository;
        this.alertService = alertService;
        this.clock = clock;
        this.metrics = metrics;
        this.retentionDays = retentionDays;
        this.resultsPerQuery = resultsPerQuery;
    }

    public List<OfferProvider> providers() {
        return providers;
    }

    @Scheduled(cron = "${app.offers.sync-cron:0 15 */6 * * *}")
    public void scheduledSync() {
        SyncSummary s = syncAll(null);
        log.info("Synchronisation des offres : {} nouvelles, {} mises à jour, {} alertes", s.created(), s.updated(), s.alertsSent());
    }

    /** Synchronise tous les fournisseurs configurés (ou un seul). */
    public SyncSummary syncAll(OfferSource only) {
        List<SyncResult> results = new ArrayList<>();
        List<Long> created = new ArrayList<>();
        int updated = 0;
        for (OfferProvider p : providers) {
            if (only != null && p.source() != only) continue;
            if (!p.isConfigured()) continue;
            List<OfferQuery> queries = new ArrayList<>();
            for (OfferCountry c : p.countries()) {
                for (OfferKind k : KINDS) {
                    if (queries.size() < p.maxQueriesPerSync()) queries.add(new OfferQuery(null, c, k, resultsPerQuery));
                }
            }
            SyncResult r = run(p, queries, created);
            results.add(r);
            updated += r.updated();
        }
        cleanup();
        if (cacheManager != null) ma.ump.smartdept.config.CacheConfig.evictOffers(cacheManager);
        int alerts = alertService.dispatch(created);
        return new SyncSummary(results, created.size(), updated, alerts);
    }

    /** Recherche à la demande d'un utilisateur : une requête par fournisseur couvrant le pays choisi. */
    public SyncSummary liveSearch(User user, LiveSearchRequest req) {
        Instant now = clock.instant();
        Instant previous = lastLiveSearch.get(user.getId());
        if (previous != null && Duration.between(previous, now).getSeconds() < 30) {
            throw ApiException.badRequest("Patientez quelques secondes avant de relancer une recherche en direct.");
        }
        lastLiveSearch.put(user.getId(), now);
        List<SyncResult> results = new ArrayList<>();
        List<Long> created = new ArrayList<>();
        int updated = 0;
        for (OfferProvider p : providers) {
            if (!p.isConfigured()) continue;
            List<OfferCountry> countries = req.country() == null ? p.countries()
                    : p.countries().contains(req.country()) ? List.of(req.country()) : List.of();
            if (countries.isEmpty()) continue;
            List<OfferQuery> queries = countries.stream().limit(2)
                    .map(c -> new OfferQuery(req.keywords(), c, req.kind() == null ? OfferKind.STAGE : req.kind(), resultsPerQuery))
                    .toList();
            SyncResult r = run(p, queries, created);
            results.add(r);
            updated += r.updated();
        }
        if (cacheManager != null && (!created.isEmpty() || updated > 0)) ma.ump.smartdept.config.CacheConfig.evictOffers(cacheManager);
        int alerts = alertService.dispatch(created);
        return new SyncSummary(results, created.size(), updated, alerts);
    }

    private SyncResult run(OfferProvider p, List<OfferQuery> queries, List<Long> createdIds) {
        Instant start = clock.instant();
        Instant last = lastCall.get(p.source());
        if (last != null && p.minIntervalMinutes() > 0
                && Duration.between(last, start).toMinutes() < p.minIntervalMinutes()) {
            return new SyncResult(p.source(), p.source().getLabel(), null, 0, 0, 0, true,
                    "Ignoré : dernier appel il y a moins de " + p.minIntervalMinutes() + " min (conditions de l'API)", 0);
        }
        lastCall.put(p.source(), start);
        int fetched = 0, created = 0, updated = 0;
        List<String> errors = new ArrayList<>();
        for (OfferQuery q : queries) {
            try {
                List<FetchedOffer> list = p.fetch(q);
                fetched += list.size();
                OfferStore.UpsertResult res = store.upsert(p.source(), list, LocalDateTime.now(clock));
                created += res.createdIds().size();
                updated += res.updated();
                createdIds.addAll(res.createdIds());
            } catch (Exception e) {
                String msg = q.country() + "/" + q.kind() + " : " + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : " " + e.getMessage());
                errors.add(msg.length() > 160 ? msg.substring(0, 160) : msg);
                log.warn("Fournisseur {} en erreur ({})", p.source(), msg);
            }
        }
        long duration = Duration.between(start, clock.instant()).toMillis();
        boolean success = errors.size() < queries.size() || queries.isEmpty();
        // Métriques Prometheus : offres reçues / créées et appels en échec, par source (tableau de bord Grafana)
        String src = p.source().name();
        Counter.builder("smartdept.offers.fetched").tag("source", src).description("Offres reçues des API").register(metrics).increment(fetched);
        Counter.builder("smartdept.offers.created").tag("source", src).description("Nouvelles offres enregistrées").register(metrics).increment(created);
        Counter.builder("smartdept.offers.errors").tag("source", src).description("Requêtes d'API en échec").register(metrics).increment(errors.size());
        String message = errors.isEmpty() ? queries.size() + " requête(s)" : String.join(" | ", errors);
        OfferSyncRun run = new OfferSyncRun();
        run.setSource(p.source());
        run.setStartedAt(LocalDateTime.ofInstant(start, clock.getZone()));
        run.setDurationMs(duration);
        run.setFetched(fetched);
        run.setCreatedCount(created);
        run.setUpdatedCount(updated);
        run.setSuccess(success);
        run.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
        runRepository.save(run);
        return new SyncResult(p.source(), p.source().getLabel(), queries.size() == 1 ? queries.get(0).country() : null,
                fetched, created, updated, success, run.getMessage(), duration);
    }

    /** Retire de la recherche les offres externes non revues depuis app.offers.retention-days et les offres expirées. */
    @Transactional
    public void cleanup() {
        offerRepository.deactivateStale(LocalDateTime.now(clock).minusDays(retentionDays));
        offerRepository.deactivateExpired(LocalDate.now(clock));
    }
}
