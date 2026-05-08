package dev.simplecore.simplix.auth.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed blacklist probe that issues a direct {@code hasKey} against
 * the configured {@link RedisTemplate}, bypassing failure-mode catches in
 * the {@code RedisTokenBlacklistService}.
 *
 * <p>Registration mirrors {@code RedisTokenBlacklistService} exactly,
 * including the {@code blacklist-store} expression gate. Without that gate
 * this probe would be registered whenever Redis is on the classpath, even
 * when {@code blacklist-store} resolves to {@code NATS}, {@code CAFFEINE},
 * or {@code MEMORY} — in which case the active {@code TokenBlacklistService}
 * is not Redis-backed and probing Redis would produce a false-positive
 * health signal that hides outages of the actual blacklist store.</p>
 */
@Component
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
@ConditionalOnBean(name = "redisTemplate")
@ConditionalOnProperty(name = "simplix.auth.token.enable-blacklist", havingValue = "true")
@ConditionalOnExpression(
        "'${simplix.auth.token.blacklist-store:AUTO}'.equalsIgnoreCase('AUTO') "
                + "or '${simplix.auth.token.blacklist-store:AUTO}'.equalsIgnoreCase('REDIS')")
public class RedisBlacklistProbe implements BlacklistProbe {

    private static final String PROBE_KEY = "simplix:token:bl:health-check-probe";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisBlacklistProbe(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void probe() {
        redisTemplate.hasKey(PROBE_KEY);
    }
}
