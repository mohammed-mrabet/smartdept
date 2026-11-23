package ma.ump.smartdept.offers;

import ma.ump.smartdept.offers.provider.FetchedOffer;
import ma.ump.smartdept.offers.provider.ProviderSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Enregistre les offres récupérées : création ou mise à jour selon (source, externalId), puis classification. */
@Service
public class OfferStore {

    public record UpsertResult(List<Long> createdIds, int updated) {
    }

    private final InternshipOfferRepository repository;

    public OfferStore(InternshipOfferRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UpsertResult upsert(OfferSource source, List<FetchedOffer> fetched, LocalDateTime now) {
        List<FetchedOffer> valid = fetched.stream()
                .filter(f -> f.externalId() != null && f.title() != null && f.url() != null)
                .collect(Collectors.toMap(FetchedOffer::externalId, Function.identity(), (a, b) -> a))
                .values().stream().toList();
        Set<String> ids = valid.stream().map(FetchedOffer::externalId).collect(Collectors.toSet());
        Map<String, InternshipOffer> existing = ids.isEmpty() ? Map.of()
                : repository.findBySourceAndExternalIdIn(source, ids).stream()
                .collect(Collectors.toMap(InternshipOffer::getExternalId, Function.identity()));
        List<InternshipOffer> created = new ArrayList<>();
        int updated = 0;
        for (FetchedOffer f : valid) {
            InternshipOffer offer = existing.get(f.externalId());
            if (offer == null) {
                offer = new InternshipOffer();
                offer.setSource(source);
                offer.setExternalId(ProviderSupport.truncate(f.externalId(), 120));
                created.add(offer);
            } else {
                updated++;
            }
            apply(offer, f, now);
            repository.save(offer);
        }
        return new UpsertResult(created.stream().map(InternshipOffer::getId).filter(Objects::nonNull).toList(), updated);
    }

    /** Copie les champs et déduit type, niveaux, compétences, durée et télétravail. */
    static void apply(InternshipOffer o, FetchedOffer f, LocalDateTime now) {
        String description = OfferClassifier.plainText(f.description());
        String text = f.title() + "\n" + (f.kindHint() == null ? "" : f.kindHint()) + "\n" + (description == null ? "" : description);
        o.setTitle(ProviderSupport.truncate(OfferClassifier.plainText(f.title()), 255));
        o.setCompany(ProviderSupport.truncate(f.company(), 200));
        o.setLocation(ProviderSupport.truncate(f.location(), 200));
        OfferCountry country = f.country() == null ? OfferCountry.OTHER : f.country();
        if (country == OfferCountry.REMOTE || country == OfferCountry.OTHER) {
            country = OfferClassifier.country(f.location(), country);
        }
        o.setCountry(country);
        o.setDescription(description);
        o.setUrl(ProviderSupport.truncate(f.url(), 1000));
        o.setSalary(ProviderSupport.truncate(f.salary(), 150));
        o.setPublishedAt(f.publishedAt() != null ? f.publishedAt() : now.toLocalDate());
        o.setKind(OfferClassifier.kind(f.title(), description, f.kindHint()));
        int[] levels = f.levelMin() != null ? new int[]{f.levelMin(), f.levelMax() == null ? f.levelMin() : f.levelMax()}
                : OfferClassifier.levels(text);
        o.setLevelMin(levels == null ? null : levels[0]);
        o.setLevelMax(levels == null ? null : levels[1]);
        o.setSkills(ProviderSupport.truncate(String.join(", ", OfferClassifier.skills(text, 8)), 500));
        o.setDurationMonths(OfferClassifier.durationMonths(text));
        o.setRemote(f.remote() != null ? f.remote() : OfferClassifier.remote(f.title() + " " + f.location() + " " + description));
        o.setActive(true);
        o.setFetchedAt(now);
    }
}
