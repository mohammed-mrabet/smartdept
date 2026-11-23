package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.common.Json;
import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * InfoJobs — premier site d'emploi en Espagne, API gratuite (application à créer sur developer.infojobs.net).
 * GET https://api.infojobs.net/api/9/offer?q=… avec authentification Basic client_id:client_secret.
 */
@Component
public class InfoJobsProvider implements OfferProvider {

    private final ProviderHttp http;
    private final String clientId;
    private final String clientSecret;
    private final String searchUrl;

    public InfoJobsProvider(ProviderHttp http,
                            @Value("${app.offers.infojobs.client-id:}") String clientId,
                            @Value("${app.offers.infojobs.client-secret:}") String clientSecret,
                            @Value("${app.offers.infojobs.search-url:https://api.infojobs.net/api/9/offer}") String searchUrl) {
        this.http = http;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.searchUrl = searchUrl;
    }

    @Override
    public OfferSource source() {
        return OfferSource.INFOJOBS;
    }

    @Override
    public List<OfferCountry> countries() {
        return List.of(OfferCountry.ES);
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    @Override
    public String configHint() {
        return "INFOJOBS_CLIENT_ID et INFOJOBS_CLIENT_SECRET (application gratuite sur developer.infojobs.net)";
    }

    @Override
    public List<FetchedOffer> fetch(OfferQuery q) {
        String auth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> body = http.get(ProviderHttp.url(searchUrl, ProviderHttp.params(
                        "q", ProviderSupport.keywords(q.keywords(), q.kind(), OfferCountry.ES),
                        "maxResults", Math.min(q.limit(), 50), "order", "updated-desc")),
                Map.of("Authorization", "Basic " + auth));
        List<FetchedOffer> out = new ArrayList<>();
        for (Object o : Json.list(body, "offers")) {
            String city = Json.str(o, "city");
            String province = Json.str(o, "province.value");
            String location = city == null ? province : province == null || province.equalsIgnoreCase(city) ? city : city + ", " + province;
            String remote = Json.str(o, "teleworking.value");
            out.add(new FetchedOffer(Json.str(o, "id"), Json.str(o, "title"), Json.str(o, "author.name"), location,
                    OfferCountry.ES, Json.first(o, "requirementMin", "description"), Json.str(o, "link"),
                    Json.str(o, "salaryDescription"), ProviderSupport.date(Json.first(o, "published", "updated")),
                    Json.str(o, "contractType.value"),
                    remote != null && !remote.toLowerCase().contains("presencial"), null, null));
        }
        return out;
    }
}
