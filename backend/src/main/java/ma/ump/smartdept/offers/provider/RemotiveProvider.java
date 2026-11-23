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
 * Remotive — offres en télétravail, API publique sans clé (GET https://remotive.com/api/remote-jobs).
 * Conditions : au plus 4 appels par jour, lien vers l'offre d'origine et mention de la source (affichés dans l'interface).
 */
@Component
public class RemotiveProvider implements OfferProvider {

    private final ProviderHttp http;

    public RemotiveProvider(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public OfferSource source() {
        return OfferSource.REMOTIVE;
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
        return "Aucune clé nécessaire (4 appels par jour au maximum)";
    }

    @Override
    public int minIntervalMinutes() {
        return 360;
    }

    @Override
    public int maxQueriesPerSync() {
        return 1;
    }

    @Override
    public List<FetchedOffer> fetch(OfferQuery q) {
        Map<String, Object> body = http.get(ProviderHttp.url("https://remotive.com/api/remote-jobs",
                ProviderHttp.params("category", "software-dev", "limit", Math.min(q.limit(), 100))), Map.of());
        List<FetchedOffer> out = new ArrayList<>();
        for (Object o : Json.list(body, "jobs")) {
            String location = Json.str(o, "candidate_required_location");
            out.add(new FetchedOffer(Json.str(o, "id"), Json.str(o, "title"), Json.str(o, "company_name"), location,
                    OfferClassifier.country(location, OfferCountry.REMOTE), OfferClassifier.plainText(Json.str(o, "description")),
                    Json.str(o, "url"), Json.str(o, "salary"), ProviderSupport.date(Json.str(o, "publication_date")),
                    Json.str(o, "job_type"), true, null, null));
        }
        return out;
    }
}
