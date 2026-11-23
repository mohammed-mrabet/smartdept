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
 * Jobicy — télétravail, API publique sans clé (GET https://jobicy.com/api/v2/remote-jobs?geo=…&industry=…).
 * Interrogée pour l'Europe et le Canada, au plus une fois par heure ; l'URL d'origine est conservée.
 */
@Component
public class JobicyProvider implements OfferProvider {

    private final ProviderHttp http;

    public JobicyProvider(ProviderHttp http) {
        this.http = http;
    }

    @Override
    public OfferSource source() {
        return OfferSource.JOBICY;
    }

    @Override
    public List<OfferCountry> countries() {
        return List.of(OfferCountry.REMOTE, OfferCountry.CA);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String configHint() {
        return "Aucune clé nécessaire (1 appel par heure au maximum)";
    }

    @Override
    public int minIntervalMinutes() {
        return 60;
    }

    @Override
    public List<FetchedOffer> fetch(OfferQuery q) {
        String geo = q.country() == OfferCountry.CA ? "canada" : "europe";
        Map<String, Object> body = http.get(ProviderHttp.url("https://jobicy.com/api/v2/remote-jobs",
                ProviderHttp.params("count", Math.min(q.limit(), 50), "geo", geo, "industry", "engineering")), Map.of());
        List<FetchedOffer> out = new ArrayList<>();
        for (Object o : Json.list(body, "jobs")) {
            String level = Json.str(o, "jobLevel");
            String description = OfferClassifier.plainText(Json.first(o, "jobDescription", "jobExcerpt"));
            out.add(new FetchedOffer(Json.str(o, "id"), Json.str(o, "jobTitle"), Json.str(o, "companyName"), Json.str(o, "jobGeo"),
                    q.country() == OfferCountry.CA ? OfferCountry.CA : OfferCountry.REMOTE,
                    level == null ? description : "Niveau : " + level + "\n" + description,
                    Json.str(o, "url"), null, ProviderSupport.date(Json.str(o, "pubDate")),
                    Json.str(o, "jobType") + " " + (level == null ? "" : level), true, null, null));
        }
        return out;
    }
}
