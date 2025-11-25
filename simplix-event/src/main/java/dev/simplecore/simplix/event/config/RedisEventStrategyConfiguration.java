package dev.simplecore.simplix.event.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.simplix.event.strategy.RedisEventStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis event strategy configuration
 *
 * <p>IMPORTANT: This configuration class is only loaded when RedisTemplate is on the classpath.
 * The strategy class ({@link RedisEventStrategy}) will NOT be loaded if this condition is not met,
 * preventing ClassNotFoundException even though RedisEventStrategy directly imports Redis classes.
 *
 * <p>Safety mechanism:
 * <ul>
 *   <li>Uses string-based @ConditionalOnClass to avoid eager class loading</li>
 *   <li>RedisEventStrategy has no @Component annotation - not component scanned</li>
 *   <li>Created only via @Bean when this Configuration is loaded</li>
 *   <li>If Redis is missing, Configuration is not loaded, strategy is never referenced</li>
 * </ul>
 *
 * @see dev.simplecore.simplix.event.strategy.RedisEventStrategy
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
public class RedisEventStrategyConfiguration {

    /**
     * Creates Redis event strategy bean for distributed event publishing using Redis Streams
     *
     * <p>This strategy uses Redis Streams for multi-instance event broadcasting
     * with guaranteed delivery and consumer group support.
     *
     * @param redisTemplate RedisTemplate for Redis operations (optional)
     * @param objectMapper ObjectMapper for JSON serialization (optional)
     * @param eventProperties Event configuration properties (optional)
     * @return Redis event strategy instance
     */
    @Bean
    @ConditionalOnMissingBean(RedisEventStrategy.class)
    public RedisEventStrategy redisEventStrategy(
            @Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
            @Autowired(required = false) ObjectMapper objectMapper,
            @Autowired(required = false) EventProperties eventProperties) {
        log.debug("Registering RedisStreamEventStrategy");

        RedisEventStrategy strategy = new RedisEventStrategy();
        strategy.setRedisTemplate(redisTemplate);
        strategy.setObjectMapper(objectMapper);
        strategy.setEventProperties(eventProperties);

        // Initialize the strategy
        strategy.initialize();

        return strategy;
    }
}
