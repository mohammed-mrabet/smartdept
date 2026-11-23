package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferKind;

/** Requête envoyée à un fournisseur : mots-clés, pays visé, type d'offre souhaité, nombre maximum de résultats. */
public record OfferQuery(String keywords, OfferCountry country, OfferKind kind, int limit) {
}
