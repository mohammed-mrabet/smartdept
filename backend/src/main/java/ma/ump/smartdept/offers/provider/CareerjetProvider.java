package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.common.Json;
import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferKind;
import ma.ump.smartdept.offers.OfferSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Careerjet (Optioncarriere au Maroc, en France et en Belgique ; Opcionempleo en Espagne ; Careerjet.ca au Canada).
 * API v4 : GET https://search.api.careerjet.net/v4/query, authentification Basic (clé éditeur, mot de passe vide).
 * contract_type=i limite aux stages. user_ip et user_agent sont obligatoires.
 */
@Component
public class CareerjetProvider implements OfferProvider {

    private static final Map<OfferCountry, String> LOCALES = Map.of(
            OfferCountry.MA, "fr_MA", OfferCountry.FR, "fr_FR", OfferCountry.BE, "fr_BE",
            OfferCountry.ES, "es_ES", OfferCountry.CA, "fr_CA");

    private final ProviderHttp http;
    private final String apiKey;
    private final String userIp;

    public CareerjetProvider(ProviderHttp http,
                             @Value("${app.offers.careerjet.api-key:}") String apiKey,
                             @Value("${app.offers.careerjet.user-ip:127.0.0.1}") String userIp) {
        this.http = http;
        this.apiKey = apiKey;
        this.userIp = userIp;
    }

    @Override
    public OfferSource source() {
        return OfferSource.CAREERJET;
    }

    @Override
    public List<OfferCountry> countries() {
        return List.of(OfferCountry.MA, OfferCountry.FR, OfferCountry.BE, OfferCountry.ES, OfferCountry.CA);
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public String configHint() {
        return "CAREERJET_API_KEY (compte éditeur gratuit sur careerjet.com/partners) et CAREERJET_USER_IP (IP publique du serveur)";
    }

    @Override
    public List<FetchedOffer> fetch(OfferQuery q) {
        String locale = LOCALES.get(q.country());
        if (locale == null) return List.of();
        Map<String, Object> params = ProviderHttp.params(
                "locale_code", locale,
                "keywords", ProviderSupport.keywords(q.keywords(), q.kind(), q.country()),
                "page_size", Math.min(q.limit(), 100), "sort", "date",
                "user_ip", userIp, "user_agent", ProviderHttp.USER_AGENT);
        if (q.kind() == OfferKind.STAGE || q.kind() == OfferKind.PFE) {
            params.put("contract_type", "i");
        }
        String auth = Base64.getEncoder().encodeToString((apiKey + ":").getBytes(StandardCharsets.UTF_8));
        Map<String, Object> body = http.get(ProviderHttp.url("https://search.api.careerjet.net/v4/query", params),
                Map.of("Authorization", "Basic " + auth));
        List<FetchedOffer> out = new ArrayList<>();
        for (Object o : Json.list(body, "jobs")) {
            String url = Json.str(o, "url");
            String location = Json.first(o, "locations");
            out.add(FetchedOffer.of(url == null ? null : Integer.toHexString(url.hashCode()) + "-" + url.length(),
                    Json.str(o, "title"), Json.str(o, "company"), location, q.country(), Json.str(o, "description"), url,
                    Json.str(o, "salary"), ProviderSupport.date(Json.str(o, "date")),
                    q.kind() == OfferKind.STAGE || q.kind() == OfferKind.PFE ? "internship" : null));
        }
        return out;
    }
}
