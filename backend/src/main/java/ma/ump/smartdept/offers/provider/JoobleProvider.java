package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.common.Json;
import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Jooble — API REST gratuite (clé sur jooble.org/api/about). Chaque domaine pays (ma.jooble.org, fr.jooble.org…)
 * a sa propre clé : JOOBLE_KEYS="MA:clé1,FR:clé2,CA:clé3,BE:clé4,ES:clé5". C'est la principale source pour le Maroc.
 * POST {url}/{clé} avec {"keywords", "location", "ResultOnPage"}.
 */
@Component
public class JoobleProvider implements OfferProvider {

    private final ProviderHttp http;
    private final Map<OfferCountry, String> keys = new EnumMap<>(OfferCountry.class);
    private final String urlTemplate;

    public JoobleProvider(ProviderHttp http,
                          @Value("${app.offers.jooble.keys:}") String keys,
                          @Value("${app.offers.jooble.url-template:https://jooble.org/api/{key}}") String urlTemplate) {
        this.http = http;
        this.urlTemplate = urlTemplate;
        for (String pair : keys.split(",")) {
            String[] kv = pair.trim().split(":", 2);
            if (kv.length == 2 && !kv[1].isBlank()) {
                try {
                    this.keys.put(OfferCountry.valueOf(kv[0].trim().toUpperCase()), kv[1].trim());
                } catch (IllegalArgumentException ignored) {
                    // code pays inconnu : ignoré
                }
            }
        }
    }

    @Override
    public OfferSource source() {
        return OfferSource.JOOBLE;
    }

    @Override
    public List<OfferCountry> countries() {
        return List.copyOf(keys.keySet());
    }

    @Override
    public boolean isConfigured() {
        return !keys.isEmpty();
    }

    @Override
    public String configHint() {
        return "JOOBLE_KEYS=MA:clé,FR:clé,… (une clé gratuite par pays, demandée sur jooble.org/api/about)";
    }

    @Override
    public List<FetchedOffer> fetch(OfferQuery q) {
        String key = keys.get(q.country());
        if (key == null) return List.of();
        Map<String, Object> payload = Map.of(
                "keywords", ProviderSupport.keywords(q.keywords(), q.kind(), q.country()),
                "location", ProviderSupport.countryName(q.country()),
                "ResultOnPage", Math.min(q.limit(), 50));
        Map<String, Object> body = http.postJson(URI.create(urlTemplate.replace("{key}", key)), payload, Map.of());
        List<FetchedOffer> out = new ArrayList<>();
        for (Object o : Json.list(body, "jobs")) {
            out.add(FetchedOffer.of(Json.str(o, "id"), Json.str(o, "title"), Json.str(o, "company"), Json.str(o, "location"),
                    q.country(), Json.str(o, "snippet"), Json.str(o, "link"), Json.str(o, "salary"),
                    ProviderSupport.date(Json.str(o, "updated")), Json.str(o, "type")));
        }
        return out;
    }
}
