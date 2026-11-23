package ma.ump.smartdept.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitation du nombre de tentatives (connexion, inscription, mot de passe oublié, contact).
 * Compteurs dans Redis (INCR + EXPIRE, partagés entre les instances) avec RATE_LIMIT_STORE=redis,
 * sinon en mémoire. Si Redis ne répond pas, on bascule sur la mémoire plutôt que de bloquer les utilisateurs.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private record Window(Instant start, int count) {
    }

    private final boolean useRedis;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final Map<String, Window> memory = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${app.ratelimit.store:memory}") String store, ObjectProvider<StringRedisTemplate> redis) {
        this.useRedis = "redis".equalsIgnoreCase(store);
        this.redis = redis;
    }

    /** Compte une tentative ; lève 429 au-delà de max tentatives dans la fenêtre. */
    public void check(String key, int max, Duration window, String message) {
        if (hit(key, window) > max) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    /** Remet un compteur à zéro (ex. connexion réussie). */
    public void reset(String key) {
        memory.remove(key);
        if (useRedis) {
            try {
                StringRedisTemplate t = redis.getIfAvailable();
                if (t != null) t.delete("smartdept::rl::" + key);
            } catch (RuntimeException e) {
                log.debug("Redis indisponible : {}", e.getMessage());
            }
        }
    }

    long hit(String key, Duration window) {
        if (useRedis) {
            try {
                StringRedisTemplate t = redis.getIfAvailable();
                if (t != null) {
                    String k = "smartdept::rl::" + key;
                    Long n = t.opsForValue().increment(k);
                    if (n != null && n == 1) t.expire(k, window);
                    return n == null ? 1 : n;
                }
            } catch (RuntimeException e) {
                log.warn("Redis indisponible pour la limitation de débit, bascule en mémoire : {}", e.getMessage());
            }
        }
        Instant now = Instant.now();
        Window w = memory.compute(key, (k, old) -> old == null || old.start().plus(window).isBefore(now)
                ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        return w.count();
    }
}
