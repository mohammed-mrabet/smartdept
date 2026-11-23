package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.common.Json;
import ma.ump.smartdept.offers.OfferClassifier;
import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arbeitnow — offres européennes (surtout télétravail et Allemagne), API publique sans clé.
 * GET https://www.arbeitnow.com/api/job-board-api ; seules les offres informatiques sont conservées.
 */
@Component
public class ArbeitnowProvider implements OfferProvider {

    private final ProviderHttp http;

    public ArbeitnowProvider(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public OfferSource source() {
        return OfferSource.ARBEITNOW;
    }

    @Override
    public List<OfferCountry> countries() {
        return List.of(OfferCountry.REMOTE);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String configHint() {
        return "Aucune clé nécessaire";
    }

    @Override
    public int minIntervalMinutes() {
        return 60;
    }

    @Override
    public int maxQueriesPerSync() {
        return 1;
    }

    @Override
    public List<FetchedOffer> fetch(OfferQuery q) {
        Map<String, Object> body = http.get(ProviderHttp.url("https://www.arbeitnow.com/api/job-board-api", Map.of()), Map.of());
        List<FetchedOffer> out = new ArrayList<>();
        for (Object o : Json.list(body, "data")) {
            String title = Json.str(o, "title");
            String tags = String.join(" ", Json.list(o, "tags").stream().map(String::valueOf).toList());
            String description = OfferClassifier.plainText(Json.str(o, "description"));
            if (OfferClassifier.skills(title + " " + tags + " " + description, 1).isEmpty()) continue;
            String location = Json.str(o, "location");
            boolean remote = Json.bool(o, "remote");
            out.add(new FetchedOffer(Json.str(o, "slug"), title, Json.str(o, "company_name"), location,
                    OfferClassifier.country(location, remote ? OfferCountry.REMOTE : OfferCountry.OTHER), description,
                    Json.str(o, "url"), null, ProviderSupport.date(Json.get(o, "created_at")),
                    String.join(" ", Json.list(o, "job_types").stream().map(String::valueOf).toList()), remote, null, null));
            if (out.size() >= q.limit()) break;
        }
        return out;
    }
}
