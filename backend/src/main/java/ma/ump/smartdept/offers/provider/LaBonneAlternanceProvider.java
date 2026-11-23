package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.common.Json;
import ma.ump.smartdept.offers.OfferClassifier;
import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * La bonne alternance (api.apprentissage.beta.gouv.fr) — offres d'alternance en France, gratuites avec un compte.
 * Recherche par codes ROME du numérique (M1805 études et développement informatique, M1810 production et exploitation,
 * M1802 expertise et support, M1806 conseil et maîtrise d'ouvrage). Le niveau visé (cadre européen) donne Bac+2 à Bac+5.
 * Chemin et paramètres configurables : se référer à la documentation Swagger de l'API si elle évolue.
 */
@Component
public class LaBonneAlternanceProvider implements OfferProvider {

    private final ProviderHttp http;
    private final String apiKey;
    private final String searchUrl;
    private final String romes;

    public LaBonneAlternanceProvider(ProviderHttp http,
                                     @Value("${app.offers.lba.api-key:}") String apiKey,
                                     @Value("${app.offers.lba.search-url:https://api.apprentissage.beta.gouv.fr/api/job/v1/search}") String searchUrl,
                                     @Value("${app.offers.lba.romes:M1805,M1810,M1802,M1806}") String romes) {
        this.http = http;
        this.apiKey = apiKey;
        this.searchUrl = searchUrl;
        this.romes = romes;
    }

    @Override
    public OfferSource source() {
        return OfferSource.LA_BONNE_ALTERNANCE;
    }

    @Override
    public List<OfferCountry> countries() {
        return List.of(OfferCountry.FR);
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public String configHint() {
        return "LBA_API_KEY (jeton obtenu sur api.apprentissage.beta.gouv.fr, page « Mon compte »)";
    }

    @Override
    public int maxQueriesPerSync() {
        return 1;
    }

    @Override
    public List<FetchedOffer> fetch(OfferQuery q) {
        Map<String, Object> body = http.get(ProviderHttp.url(searchUrl, ProviderHttp.params("romes", romes)),
                Map.of("Authorization", "Bearer " + apiKey));
        List<FetchedOffer> out = new ArrayList<>();
        for (Object o : Json.list(body, "jobs")) {
            String id = Json.first(o, "identifier.id", "identifier.partner_job_id", "id", "partner_job_id");
            Integer level = OfferClassifier.levelFromEuropean(Json.first(o, "offer.target_diploma.european", "offer.target_diploma"));
            out.add(new FetchedOffer(id, Json.str(o, "offer.title"), Json.first(o, "workplace.name", "workplace.legal_name"),
                    Json.first(o, "workplace.location.address", "location.address"), OfferCountry.FR,
                    Json.str(o, "offer.description"), Json.first(o, "apply.url"), null,
                    ProviderSupport.date(Json.first(o, "offer.publication.creation", "publication.creation")),
                    "apprentissage", null, level, level));
            if (out.size() >= q.limit()) break;
        }
        return out;
    }
}
