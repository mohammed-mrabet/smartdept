package ma.ump.smartdept.offers;

import ma.ump.smartdept.common.ApiException;
import ma.ump.smartdept.common.StudyLevel;
import ma.ump.smartdept.notification.NotificationService;
import ma.ump.smartdept.notification.NotificationType;
import ma.ump.smartdept.offers.OfferDtos.*;
import ma.ump.smartdept.offers.provider.OfferProvider;
import ma.ump.smartdept.user.Role;
import ma.ump.smartdept.user.User;
import ma.ump.smartdept.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Recherche, offres internes, suivi des candidatures, alertes, sources et statistiques. */
@Service
@Transactional
public class OfferService {

    private final InternshipOfferRepository offerRepository;
    private final OfferBookmarkRepository bookmarkRepository;
    private final OfferAlertRepository alertRepository;
    private final OfferSyncRunRepository runRepository;
    private final OfferSyncService syncService;
    private final OfferAlertService alertService;
    private final UserRepository userRepository;
    private final NotificationService notifications;
    private final Clock clock;

    public OfferService(InternshipOfferRepository offerRepository, OfferBookmarkRepository bookmarkRepository,
                        OfferAlertRepository alertRepository, OfferSyncRunRepository runRepository,
                        OfferSyncService syncService, OfferAlertService alertService, UserRepository userRepository,
                        NotificationService notifications, Clock clock) {
        this.offerRepository = offerRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.alertRepository = alertRepository;
        this.runRepository = runRepository;
        this.syncService = syncService;
        this.alertService = alertService;
        this.userRepository = userRepository;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- recherche

    @Transactional(readOnly = true)
    public OfferPage search(User me, String q, OfferCountry country, OfferKind kind, OfferSource source, boolean remoteOnly,
                            Integer level, boolean includeUnknown, String sort, int page, int size) {
        int pageSize = Math.max(1, Math.min(size, 50));
        int pageIndex = Math.max(0, page);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        boolean student = me.getRole() == Role.ETUDIANT;
        OfferAiService.Profile profile = student ? OfferAiService.profile(me) : null;
        Map<Long, TrackingStatus> tracked = trackedBy(me);

        List<OfferResponse> items;
        long total;
        if ("match".equals(sort) && student) {
            // Tri par score : calculé sur les 300 offres les plus récentes qui passent les filtres
            List<InternshipOffer> candidates = offerRepository.search(like, country, kind, source, remoteOnly, level,
                    includeUnknown, PageRequest.of(0, 300, Sort.by(Sort.Direction.DESC, "publishedAt", "id"))).getContent();
            List<OfferResponse> scored = candidates.stream()
                    .map(o -> OfferResponse.of(o, false, OfferAiService.score(profile, o), tracked.get(o.getId())))
                    .sorted(Comparator.comparing(OfferResponse::match).reversed())
                    .toList();
            total = scored.size();
            items = scored.stream().skip((long) pageIndex * pageSize).limit(pageSize).toList();
        } else {
            Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
            Page<InternshipOffer> result = offerRepository.search(like, country, kind, source, remoteOnly, level, includeUnknown, pageable);
            total = result.getTotalElements();
            items = result.getContent().stream()
                    .map(o -> OfferResponse.of(o, false, student ? OfferAiService.score(profile, o) : null, tracked.get(o.getId())))
                    .toList();
        }
        int pages = (int) Math.ceil(total / (double) pageSize);
        return new OfferPage(items, total, pageIndex, pageSize, pages, facets(), profile == null ? null : profile.level());
    }

    @Transactional(readOnly = true)
    public OfferResponse get(User me, Long id) {
        InternshipOffer o = find(id);
        Integer match = me.getRole() == Role.ETUDIANT ? OfferAiService.score(OfferAiService.profile(me), o) : null;
        return OfferResponse.of(o, true, match, trackedBy(me).get(id));
    }

    /** Offres recommandées : niveau compatible, meilleur score de correspondance. */
    @Transactional(readOnly = true)
    public List<OfferResponse> recommended(User me, int limit) {
        OfferAiService.Profile profile = OfferAiService.profile(me);
        Map<Long, TrackingStatus> tracked = trackedBy(me);
        return offerRepository.findTop300ByActiveTrueOrderByPublishedAtDesc().stream()
                .filter(o -> profile.level() == null || o.acceptsLevel(profile.level()))
                .map(o -> OfferResponse.of(o, false, OfferAiService.score(profile, o), tracked.get(o.getId())))
                .sorted(Comparator.comparing(OfferResponse::match).reversed())
                .limit(Math.max(1, Math.min(limit, 30)))
                .toList();
    }

    public InternshipOffer find(Long id) {
        return offerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Offre introuvable : " + id));
    }

    private Map<Long, TrackingStatus> trackedBy(User me) {
        Map<Long, TrackingStatus> map = new HashMap<>();
        for (Object[] row : bookmarkRepository.statusesForUser(me.getId())) {
            map.put((Long) row[0], (TrackingStatus) row[1]);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public Facets facets() {
        Map<String, Long> levels = new LinkedHashMap<>();
        for (StudyLevel l : StudyLevel.values()) levels.put(l.name(), 0L);
        long unknown = 0;
        for (Object[] r : offerRepository.levelRanges()) {
            Integer min = (Integer) r[0];
            Integer max = (Integer) r[1];
            if (min == null) {
                unknown++;
                continue;
            }
            for (StudyLevel l : StudyLevel.values()) {
                if (l.getYears() >= min && l.getYears() <= (max == null ? min : max)) levels.merge(l.name(), 1L, Long::sum);
            }
        }
        return new Facets(toMap(offerRepository.countByCountry()), toMap(offerRepository.countByKind()), levels,
                toMap(offerRepository.countBySource()), unknown);
    }

    private static Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        rows.stream().sorted((a, b) -> Long.compare((Long) b[1], (Long) a[1]))
                .forEach(r -> map.put(String.valueOf(r[0]), (Long) r[1]));
        return map;
    }

    // ---------------------------------------------------------------- offres internes

    public OfferResponse create(User me, OfferRequest r) {
        requireTeacher(me);
        InternshipOffer o = new InternshipOffer();
        o.setSource(OfferSource.INTERNAL);
        o.setExternalId(UUID.randomUUID().toString());
        o.setPostedBy(me);
        apply(o, r);
        offerRepository.save(o);
        announce(o);
        alertService.dispatch(List.of(o.getId()));
        return OfferResponse.of(o, true, null, null);
    }

    public OfferResponse update(User me, Long id, OfferRequest r) {
        InternshipOffer o = requireOwnInternal(me, id);
        apply(o, r);
        return OfferResponse.of(o, true, null, null);
    }

    public OfferResponse close(User me, Long id) {
        InternshipOffer o = requireOwnInternal(me, id);
        o.setActive(false);
        return OfferResponse.of(o, true, null, null);
    }

    public void delete(User me, Long id) {
        InternshipOffer o = requireOwnInternal(me, id);
        offerRepository.delete(o);
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> postedBy(User me) {
        return offerRepository.findByPostedByIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(o -> OfferResponse.of(o, false, null, null)).toList();
    }

    private void apply(InternshipOffer o, OfferRequest r) {
        Integer min = r.levelMin();
        Integer max = r.levelMax();
        if (min != null && max != null && max < min) {
            throw ApiException.badRequest("Le niveau maximum doit être supérieur ou égal au niveau minimum");
        }
        if (r.expiresAt() != null && r.expiresAt().isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("La date limite est déjà passée");
        }
        String text = r.title() + "\n" + r.description();
        o.setTitle(r.title().trim());
        o.setCompany(r.company().trim());
        o.setLocation(r.location().trim());
        o.setCountry(r.country());
        o.setKind(r.kind());
        o.setLevelMin(min != null ? min : max);
        o.setLevelMax(max != null ? max : min);
        o.setRemote(r.remote());
        o.setDescription(r.description().trim());
        o.setUrl(blank(r.url()));
        o.setContactEmail(blank(r.contactEmail()));
        o.setDurationMonths(r.durationMonths() != null ? r.durationMonths() : OfferClassifier.durationMonths(text));
        o.setSalary(blank(r.salary()));
        o.setExpiresAt(r.expiresAt());
        String skills = r.skills() != null && !r.skills().isBlank() ? r.skills().trim()
                : String.join(", ", OfferClassifier.skills(text, 8));
        o.setSkills(skills);
        o.setPublishedAt(o.getPublishedAt() == null ? LocalDate.now(clock) : o.getPublishedAt());
        o.setFetchedAt(LocalDateTime.now(clock));
        o.setActive(true);
        if (o.getUrl() == null && o.getContactEmail() == null) {
            throw ApiException.badRequest("Indiquez un lien pour postuler ou un e-mail de contact");
        }
    }

    /** Une offre du département est annoncée aux étudiants dont le niveau correspond. */
    private void announce(InternshipOffer o) {
        List<User> students = userRepository.findByRoleAndEnabledTrueOrderByLastNameAsc(Role.ETUDIANT).stream()
                .filter(s -> s.getFiliere() == null || s.getFiliere().getStudyLevel() == null
                        || o.acceptsLevel(s.getFiliere().getStudyLevel().getYears()))
                .toList();
        notifications.notifyAll(students, NotificationType.OFFER_PUBLISHED,
                "Nouvelle offre : " + o.getTitle(),
                o.getCompany() + " · " + o.getLocation() + " · " + OfferDtos.levelLabel(o.getLevelMin(), o.getLevelMax())
                        + ". Offre publiée par le département.",
                "/offres?offre=" + o.getId());
    }

    private void requireTeacher(User me) {
        if (me.getRole() == Role.ETUDIANT) {
            throw ApiException.forbidden("Seuls les enseignants et le chef de département publient des offres");
        }
    }

    private InternshipOffer requireOwnInternal(User me, Long id) {
        InternshipOffer o = find(id);
        if (!o.isInternal()) {
            throw ApiException.badRequest("Seules les offres publiées dans SmartDept peuvent être modifiées");
        }
        boolean owner = o.getPostedBy() != null && o.getPostedBy().getId().equals(me.getId());
        if (!owner && !me.getRole().isManager()) {
            throw ApiException.forbidden("Vous ne pouvez modifier que vos propres offres");
        }
        return o;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ---------------------------------------------------------------- suivi des candidatures

    @Transactional(readOnly = true)
    public List<TrackingResponse> tracking(User me) {
        return bookmarkRepository.findForUser(me.getId()).stream().map(TrackingResponse::of).toList();
    }

    public TrackingResponse track(User me, Long offerId, TrackingRequest r) {
        InternshipOffer offer = find(offerId);
        OfferBookmark b = bookmarkRepository.findByUserIdAndOfferId(me.getId(), offerId).orElseGet(() -> {
            OfferBookmark nb = new OfferBookmark();
            nb.setUser(me);
            nb.setOffer(offer);
            return nb;
        });
        if (r.status() != b.getStatus()) {
            b.setFollowUpSent(false);
        }
        if (r.status() == TrackingStatus.APPLIED && b.getAppliedAt() == null) {
            b.setAppliedAt(LocalDate.now(clock));
        }
        b.setStatus(r.status());
        if (r.notes() != null) {
            b.setNotes(r.notes().isBlank() ? null : r.notes().trim());
        }
        return TrackingResponse.of(bookmarkRepository.save(b));
    }

    public void untrack(User me, Long offerId) {
        bookmarkRepository.findByUserIdAndOfferId(me.getId(), offerId).ifPresent(bookmarkRepository::delete);
    }

    // ---------------------------------------------------------------- alertes

    @Transactional(readOnly = true)
    public List<AlertResponse> alerts(User me) {
        return alertRepository.findByUserIdOrderByCreatedAtDesc(me.getId()).stream().map(AlertResponse::of).toList();
    }

    public AlertResponse createAlert(User me, AlertRequest r) {
        if (alertRepository.findByUserIdOrderByCreatedAtDesc(me.getId()).size() >= 10) {
            throw ApiException.badRequest("10 alertes au maximum : supprimez-en une avant d'en créer une nouvelle");
        }
        OfferAlert a = new OfferAlert();
        a.setUser(me);
        applyAlert(a, r);
        return AlertResponse.of(alertRepository.save(a));
    }

    public AlertResponse updateAlert(User me, Long id, AlertRequest r) {
        OfferAlert a = ownAlert(me, id);
        applyAlert(a, r);
        return AlertResponse.of(a);
    }

    public void deleteAlert(User me, Long id) {
        alertRepository.delete(ownAlert(me, id));
    }

    private OfferAlert ownAlert(User me, Long id) {
        OfferAlert a = alertRepository.findById(id).orElseThrow(() -> ApiException.notFound("Alerte introuvable : " + id));
        if (!a.getUser().getId().equals(me.getId())) throw ApiException.forbidden("Cette alerte ne vous appartient pas");
        return a;
    }

    private static void applyAlert(OfferAlert a, AlertRequest r) {
        a.setName(r.name().trim());
        a.setKeywords(blank(r.keywords()));
        a.setCountry(r.country());
        a.setKind(r.kind());
        a.setStudyLevel(r.studyLevel());
        a.setRemoteOnly(r.remoteOnly());
        a.setActive(r.active() == null || r.active());
    }

    // ---------------------------------------------------------------- sources et statistiques

    @Transactional(readOnly = true)
    public List<SourceStatus> sources() {
        List<SourceStatus> list = new ArrayList<>();
        list.add(new SourceStatus(OfferSource.INTERNAL, OfferSource.INTERNAL.getLabel(), OfferSource.INTERNAL.getWebsite(), true, true,
                List.of(OfferCountry.MA, OfferCountry.FR, OfferCountry.CA, OfferCountry.BE, OfferCountry.ES),
                "Offres publiées par les enseignants et le chef de département (entreprises partenaires)",
                offerRepository.countBySourceAndActiveTrue(OfferSource.INTERNAL), 0, null));
        for (OfferProvider p : syncService.providers()) {
            list.add(new SourceStatus(p.source(), p.source().getLabel(), p.source().getWebsite(), p.isConfigured(),
                    p.configHint().startsWith("Aucune"), p.countries(), p.configHint(),
                    offerRepository.countBySourceAndActiveTrue(p.source()), p.minIntervalMinutes(),
                    RunInfo.of(runRepository.findTopBySourceOrderByStartedAtDesc(p.source()).orElse(null))));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public OfferStats stats() {
        Facets f = facets();
        Map<String, Long> skills = new HashMap<>();
        for (String s : offerRepository.allSkills()) {
            for (String skill : OfferDtos.skillList(s)) skills.merge(skill, 1L, Long::sum);
        }
        List<NameCount> topSkills = skills.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(12)
                .map(e -> new NameCount(e.getKey(), e.getValue())).toList();
        List<NameCount> topCompanies = offerRepository.topCompanies(PageRequest.of(0, 8)).stream()
                .map(r -> new NameCount(String.valueOf(r[0]), (Long) r[1])).toList();
        Map<String, Long> tracking = new LinkedHashMap<>();
        for (TrackingStatus s : TrackingStatus.values()) tracking.put(s.name(), 0L);
        for (Object[] r : bookmarkRepository.countByStatus()) tracking.put(String.valueOf(r[0]), (Long) r[1]);
        Map<String, Long> levels = new LinkedHashMap<>(f.levels());
        levels.put("UNKNOWN", f.unknownLevel());
        return new OfferStats(offerRepository.countByActiveTrue(),
                offerRepository.countByActiveTrueAndCreatedAtAfter(LocalDateTime.now(clock).minusDays(7)),
                f.countries(), levels, f.kinds(), f.sources(), topSkills, topCompanies, tracking);
    }

    public static List<LevelInfo> levels() {
        return Arrays.stream(StudyLevel.values()).map(LevelInfo::of).collect(Collectors.toList());
    }
}
