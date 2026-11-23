package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.common.Json;
import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferKind;
import ma.ump.smartdept.offers.OfferSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * France Travail (ex-Pôle emploi), API « Offres d'emploi v2 » — gratuite après inscription sur francetravail.io.
 * Authentification OAuth2 client_credentials (realm /partenaire), jeton mis en cache jusqu'à expiration.
 * Recherche : GET /partenaire/offresdemploi/v2/offres/search?motsCles=…&range=0-49 (réponse 200 ou 206).
 */
@Component
public class FranceTravailProvider implements OfferProvider {

    private static final String TOKEN_URL = "https://entreprise.francetravail.fr/connexion/oauth2/access_token?realm=%2Fpartenaire";
    private static final String SEARCH_URL = "https://api.francetravail.io/partenaire/offresdemploi/v2/offres/search";

    private final ProviderHttp http;
    private final String clientId;
    private final String clientSecret;
    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    public FranceTravailProvider(ProviderHttp http,
                                 @Value("${app.offers.france-travail.client-id:}") String clientId,
                                 @Value("${app.offers.france-travail.client-secret:}") String clientSecret) {
        this.http = http;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public OfferSource source() {
        return OfferSource.FRANCE_TRAVAIL;
    }

    @Override
    public List<OfferCountry> countries() {
        return List.of(OfferCountry.FR);
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    @Override
    public String configHint() {
        return "FRANCE_TRAVAIL_CLIENT_ID et FRANCE_TRAVAIL_CLIENT_SECRET (application créée sur francetravail.io, API « Offres d'emploi v2 »)";
    }

    @Override
    public List<FetchedOffer> fetch(OfferQuery q) {
        Map<String, Object> params = ProviderHttp.params(
                "motsCles", ProviderSupport.keywords(q.keywords(), q.kind(), OfferCountry.FR).replace(' ', ','),
                "range", "0-" + (Math.min(q.limit(), 150) - 1),
                "publieeDepuis", 31,
                "sort", 1);
        if (q.kind() == OfferKind.ALTERNANCE) {
            params.put("natureContrat", "E2");
        }
        Map<String, Object> body = http.get(ProviderHttp.url(SEARCH_URL, params), Map.of("Authorization", "Bearer " + token()));
        List<FetchedOffer> out = new ArrayList<>();
        for (Object o : Json.list(body, "resultats")) {
            String id = Json.str(o, "id");
            String url = Json.first(o, "origineOffre.urlOrigine");
            if (url == null && id != null) url = "https://candidat.francetravail.fr/offres/recherche/detail/" + id;
            out.add(new FetchedOffer(id, Json.str(o, "intitule"), Json.first(o, "entreprise.nom"), Json.str(o, "lieuTravail.libelle"),
                    OfferCountry.FR, Json.str(o, "description"), url, Json.str(o, "salaire.libelle"),
                    ProviderSupport.date(Json.str(o, "dateCreation")),
                    Json.first(o, "natureContrat", "typeContratLibelle", "typeContrat"), null, null, null));
        }
        return out;
    }

    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpiry)) {
            return token;
        }
        Map<String, Object> res = http.postForm(java.net.URI.create(TOKEN_URL), Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_secret", clientSecret,
                "scope", "api_offresdemploiv2 o2dsoffre"));
        token = Json.str(res, "access_token");
        Double expires = Json.num(res, "expires_in");
        tokenExpiry = Instant.now().plusSeconds(expires == null ? 600 : expires.longValue() - 60);
        return token;
    }
}
