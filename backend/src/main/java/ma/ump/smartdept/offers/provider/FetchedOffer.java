package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.offers.OfferCountry;

import java.time.LocalDate;

/**
 * Offre telle que renvoyée par une API, avant classification.
 * kindHint : type de contrat fourni par l'API ("Apprentissage", "internship"…) ;
 * levelMin / levelMax : niveaux fournis par l'API, sinon déduits du texte.
 */
public record FetchedOffer(String externalId, String title, String company, String location, OfferCountry country,
                           String description, String url, String salary, LocalDate publishedAt, String kindHint,
                           Boolean remote, Integer levelMin, Integer levelMax) {

    public static FetchedOffer of(String externalId, String title, String company, String location, OfferCountry country,
                                  String description, String url, String salary, LocalDate publishedAt, String kindHint) {
        return new FetchedOffer(externalId, title, company, location, country, description, url, salary, publishedAt,
                kindHint, null, null, null);
    }
}
