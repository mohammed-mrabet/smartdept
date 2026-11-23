package ma.ump.smartdept.offers.provider;

import ma.ump.smartdept.offers.OfferCountry;
import ma.ump.smartdept.offers.OfferSource;

import java.util.List;

/**
 * Une API d'offres. Chaque implémentation est un @Component : en ajouter une suffit pour qu'elle soit synchronisée.
 * Un fournisseur non configuré (clé absente) est ignoré sans erreur.
 */
public interface OfferProvider {

    OfferSource source();

    /** Pays que ce fournisseur sait interroger. */
    List<OfferCountry> countries();

    boolean isConfigured();

    /** Ce qu'il faut renseigner pour l'activer (affiché dans la page « Sources »). */
    String configHint();

    /** Délai minimum entre deux appels, pour respecter les conditions d'utilisation (ex. Remotive : 4 appels par jour). */
    default int minIntervalMinutes() {
        return 0;
    }

    /** Nombre de requêtes par synchronisation (une par pays et par mot-clé, plafonné). */
    default int maxQueriesPerSync() {
        return 10;
    }

    List<FetchedOffer> fetch(OfferQuery query) throws Exception;
}
