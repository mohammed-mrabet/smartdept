package ma.ump.smartdept.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RateLimiterTest {

    @SuppressWarnings("unchecked")
    private final RateLimiter limiter = new RateLimiter("memory", mock(ObjectProvider.class));

    @Test
    void blocksAfterTheLimitThenResets() {
        for (int i = 0; i < 3; i++) limiter.check("login:x", 3, Duration.ofMinutes(15), "trop");
        ApiException e = assertThrows(ApiException.class, () -> limiter.check("login:x", 3, Duration.ofMinutes(15), "trop"));
        assertEquals(429, e.getStatus().value());
        limiter.reset("login:x");
        assertDoesNotThrow(() -> limiter.check("login:x", 3, Duration.ofMinutes(15), "trop"));
        assertDoesNotThrow(() -> limiter.check("login:y", 3, Duration.ofMinutes(15), "trop"), "compteurs indépendants");
    }

    @Test
    void redisUnavailableFallsBackToMemory() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenThrow(new IllegalStateException("Redis éteint"));
        RateLimiter r = new RateLimiter("redis", provider);
        assertEquals(1, r.hit("k", Duration.ofMinutes(1)));
        assertEquals(2, r.hit("k", Duration.ofMinutes(1)));
    }
}
