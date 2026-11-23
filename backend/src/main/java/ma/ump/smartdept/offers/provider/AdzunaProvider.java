package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.common.Json;
import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adzuna — agrégateur avec une API gratuite (app_id + app_key sur developer.adzuna.com).
 * Couvre la France, la Belgique, l'Espagne et le Canada : GET /v1/api/jobs/{pays}/search/1.
 */
@Component
public class AdzunaProvider implements OfferProvider {

    private static final Map<OfferCountry, String> CODES = Map.of(
            OfferCountry.FR, "fr", OfferCountry.BE, "be", OfferCountry.ES, "es", OfferCountry.CA, "ca");

    private final ProviderHttp http;
    private final String appId;
    private final String appKey;

    public AdzunaProvider(ProviderHttp http,
                          @Value("${app.offers.adzuna.app-id:}") String appId,
                          @Value("${app.offers.adzuna.app-key:}") String appKey) {
        this.http = http;
        this.appId = appId;
        this.appKey = appKey;
    }

    @Override
    public OfferSource source() {
        return OfferSource.ADZUNA;
    }

    @Override
    public List<OfferCountry> countries() {
        return List.of(OfferCountry.FR, OfferCountry.BE, OfferCountry.ES, OfferCountry.CA);
    }

    @Override
    public boolean isConfigured() {
        return !appId.isBlank() && !appKey.isBlank();
    }

    @Override
    public String configHint() {
        return "ADZUNA_APP_ID et ADZUNA_APP_KEY (compte gratuit sur developer.adzuna.com)";
    }

    @Override
    public List<FetchedOffer> fetch(OfferQuery q) {
        String code = CODES.get(q.country());
        if (code == null) return List.of();
        Map<String, Object> body = http.get(ProviderHttp.url("https://api.adzuna.com/v1/api/jobs/" + code + "/search/1",
                ProviderHttp.params("app_id", appId, "app_key", appKey,
                        "what", ProviderSupport.keywords(q.keywords(), q.kind(), q.country()),
                        "results_per_page", Math.min(q.limit(), 50), "max_days_old", 30, "sort_by", "date",
                        "content-type", "application/json")), Map.of());
        List<FetchedOffer> out = new ArrayList<>();
        for (Object o : Json.list(body, "results")) {
            Double min = Json.num(o, "salary_min");
            Double max = Json.num(o, "salary_max");
            String salary = min == null ? null : max == null || max.equals(min)
                    ? String.format("%.0f", min) : String.format("%.0f - %.0f", min, max);
            out.add(FetchedOffer.of(Json.str(o, "id"), Json.str(o, "title"), Json.str(o, "company.display_name"),
                    Json.str(o, "location.display_name"), q.country(), Json.str(o, "description"), Json.str(o, "redirect_url"),
                    salary, ProviderSupport.date(Json.str(o, "created")), Json.first(o, "contract_type", "contract_time")));
        }
        return out;
    }
}
