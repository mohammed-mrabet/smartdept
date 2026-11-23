package ma.ump.smartdept.offers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.ump.smartdept.offers.OfferDtos.*;
import ma.ump.smartdept.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/offers")
@Tag(name = "Offres de stage", description = "Agrégation d'API gratuites (France, Maroc, Canada, Belgique, Espagne, télétravail) et offres du département")
public class OfferController {

    private final OfferService service;
    private final OfferSyncService syncService;
    private final OfferAiService aiService;
    private final OfferAlertService alertService;
    private final CurrentUser currentUser;

    public OfferController(OfferService service, OfferSyncService syncService, OfferAiService aiService,
                           OfferAlertService alertService, CurrentUser currentUser) {
        this.service = service;
        this.syncService = syncService;
        this.aiService = aiService;
        this.alertService = alertService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Rechercher des offres (mots-clés, pays, type, niveau Bac+2 à Bac+5, source, télétravail)")
    public OfferPage search(@RequestParam(required = false) String q,
                            @RequestParam(required = false) OfferCountry country,
                            @RequestParam(required = false) OfferKind kind,
                            @RequestParam(required = false) OfferSource source,
                            @RequestParam(defaultValue = "false") boolean remote,
                            @RequestParam(required = false) Integer level,
                            @RequestParam(defaultValue = "true") boolean includeUnknown,
                            @RequestParam(defaultValue = "recent") String sort,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return service.search(currentUser.get(), q, country, kind, source, remote, level, includeUnknown, sort, page, size);
    }

    @GetMapping("/levels")
    @Operation(summary = "Niveaux d'études (Bac+2 à Bac+5) et diplômes correspondants")
    public List<LevelInfo> levels() {
        return OfferService.levels();
    }

    @GetMapping("/recommended")
    @Operation(summary = "Offres recommandées pour l'étudiant connecté (niveau et compétences)")
    public List<OfferResponse> recommended(@RequestParam(defaultValue = "8") int limit) {
        return service.recommended(currentUser.get(), limit);
    }

    @GetMapping("/{id}")
    public OfferResponse get(@PathVariable Long id) {
        return service.get(currentUser.get(), id);
    }

    @GetMapping("/{id}/match")
    @Operation(summary = "Score de correspondance profil / offre, compétences communes et manquantes, conseils")
    public MatchResponse match(@PathVariable Long id) {
        return aiService.match(currentUser.get(), service.find(id));
    }

    @PostMapping("/{id}/cover-letter")
    @Operation(summary = "Lettre de motivation (modèle IA, ou modèle local en français, espagnol ou anglais)")
    public CoverLetterResponse coverLetter(@PathVariable Long id, @Valid @RequestBody(required = false) CoverLetterRequest req) {
        return aiService.coverLetter(currentUser.get(), service.find(id),
                req == null ? null : req.language(), req == null ? null : req.extra());
    }

    @PostMapping("/live-search")
    @Operation(summary = "Interroger maintenant les API configurées (30 s minimum entre deux recherches)")
    public SyncSummary liveSearch(@Valid @RequestBody LiveSearchRequest req) {
        return syncService.liveSearch(currentUser.get(), req);
    }

    // --- offres du département
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    public List<OfferResponse> mine() {
        return service.postedBy(currentUser.get());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Publier une offre d'une entreprise partenaire (les étudiants du bon niveau sont notifiés)")
    public OfferResponse create(@Valid @RequestBody OfferRequest req) {
        return service.create(currentUser.get(), req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    public OfferResponse update(@PathVariable Long id, @Valid @RequestBody OfferRequest req) {
        return service.update(currentUser.get(), id, req);
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    public OfferResponse close(@PathVariable Long id) {
        return service.close(currentUser.get(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(currentUser.get(), id);
    }

    // --- suivi des candidatures
    @GetMapping("/tracking")
    @Operation(summary = "Mes offres suivies (favoris, postulé, entretien, accepté, refusé)")
    public List<TrackingResponse> tracking() {
        return service.tracking(currentUser.get());
    }

    @PutMapping("/{id}/tracking")
    public TrackingResponse track(@PathVariable Long id, @Valid @RequestBody TrackingRequest req) {
        return service.track(currentUser.get(), id, req);
    }

    @DeleteMapping("/{id}/tracking")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void untrack(@PathVariable Long id) {
        service.untrack(currentUser.get(), id);
    }

    // --- alertes
    @GetMapping("/alerts")
    public List<AlertResponse> alerts() {
        return service.alerts(currentUser.get());
    }

    @PostMapping("/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une alerte : notification et e-mail à chaque nouvelle offre correspondante")
    public AlertResponse createAlert(@Valid @RequestBody AlertRequest req) {
        return service.createAlert(currentUser.get(), req);
    }

    @PutMapping("/alerts/{id}")
    public AlertResponse updateAlert(@PathVariable Long id, @Valid @RequestBody AlertRequest req) {
        return service.updateAlert(currentUser.get(), id, req);
    }

    @DeleteMapping("/alerts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAlert(@PathVariable Long id) {
        service.deleteAlert(currentUser.get(), id);
    }

    // --- administration des sources
    @GetMapping("/sources")
    @Operation(summary = "État des API : configurée, pays couverts, offres actives, dernière synchronisation")
    public List<SourceStatus> sources() {
        return service.sources();
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Synchroniser maintenant toutes les API (ou une seule avec ?source=ADZUNA)")
    public SyncSummary sync(@RequestParam(required = false) OfferSource source) {
        return syncService.syncAll(source);
    }

    @PostMapping("/follow-ups/run")
    @PreAuthorize("hasAnyRole('CHEF_DEPARTEMENT', 'ADMIN')")
    public java.util.Map<String, Integer> followUps() {
        return java.util.Map.of("sent", alertService.sendFollowUps());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'CHEF_DEPARTEMENT', 'ADMIN')")
    @Operation(summary = "Marché des stages : répartition par pays, niveau, type, source ; compétences et entreprises les plus demandées")
    public OfferStats stats() {
        return service.stats();
    }
}
