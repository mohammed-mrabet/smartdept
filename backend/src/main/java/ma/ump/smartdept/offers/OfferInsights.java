package ma.ump.smartdept.offers;

import ma.ump.smartdept.common.StudyLevel;
import ma.ump.smartdept.config.CacheConfig;
import ma.ump.smartdept.offers.OfferDtos.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Agrégats sur les offres (compteurs des filtres, statistiques du marché), mis en cache (Redis ou mémoire) :
 * ils sont recalculés après chaque synchronisation ou publication, ou au plus tard au bout de 10 minutes.
 */
@Service
@Transactional(readOnly = true)
public class OfferInsights {

    private final InternshipOfferRepository offerRepository;
    private final OfferBookmarkRepository bookmarkRepository;
    private final Clock clock;

    public OfferInsights(InternshipOfferRepository offerRepository, OfferBookmarkRepository bookmarkRepository, Clock clock) {
        this.offerRepository = offerRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.clock = clock;
    }

    @Cacheable(CacheConfig.OFFER_FACETS)
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

    @Cacheable(CacheConfig.OFFER_STATS)
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

}
