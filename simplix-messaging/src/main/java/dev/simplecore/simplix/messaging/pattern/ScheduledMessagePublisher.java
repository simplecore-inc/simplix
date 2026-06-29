package dev.simplecore.simplix.messaging.pattern;

import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.redis.RedisScheduledMessagePublisher;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.scheduler.MessageScheduler;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * @deprecated since 1.1.1, use {@link MessageScheduler} (broker-agnostic SPI) instead.
 *             For Redis-backed delayed delivery, inject {@link RedisScheduledMessagePublisher} directly.
 *             This class will be removed in a future release.
 */
@Deprecated(since = "1.1.1", forRemoval = true)
public class ScheduledMessagePublisher implements MessageScheduler {

    private final RedisScheduledMessagePublisher delegate;

    public ScheduledMessagePublisher(BrokerStrategy brokerStrategy,
                                      StringRedisTemplate redisTemplate,
                                      String keyPrefix,
                                      Duration pollInterval) {
        this.delegate = new RedisScheduledMessagePublisher(brokerStrategy, redisTemplate, keyPrefix, pollInterval);
    }

    @Override
    public String publishDelayed(Message<?> message, Duration delay) {
        return delegate.publishDelayed(message, delay);
    }

    @Override
    public boolean cancel(String scheduleId) {
        return delegate.cancel(scheduleId);
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void stop() {
        delegate.stop();
    }
}
