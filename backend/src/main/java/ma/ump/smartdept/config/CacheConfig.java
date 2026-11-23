package ma.ump.smartdept.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * Cache des lectures fréquentes et coûteuses (statistiques de l'accueil, compteurs des offres, filières).
 * Avec CACHE_TYPE=redis, le cache est partagé entre toutes les instances du backend (Kubernetes : 2 à 5 réplicas).
 * Si Redis tombe, l'erreur est journalisée et la donnée est recalculée : l'application continue de répondre.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String PUBLIC_STATS = "publicStats";
    public static final String PUBLIC_OFFERS = "publicOffers";
    public static final String OFFER_FACETS = "offerFacets";
    public static final String OFFER_STATS = "offerStats";
    public static final String FILIERES = "filieres";

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Vide les caches qui dépendent des offres (après une synchronisation ou une publication). */
    public static void evictOffers(CacheManager manager) {
        if (manager == null) return;
        for (String name : new String[]{PUBLIC_STATS, PUBLIC_OFFERS, OFFER_FACETS, OFFER_STATS}) {
            try {
                Cache cache = manager.getCache(name);
                if (cache != null) cache.clear();
            } catch (RuntimeException e) {
                log.warn("Cache {} non vidé : {}", name, e.getMessage());
            }
        }
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache {} indisponible (lecture) : {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache {} indisponible (écriture) : {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache {} indisponible (éviction) : {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache {} indisponible (vidage) : {}", cache.getName(), e.getMessage());
            }
        };
    }
}
