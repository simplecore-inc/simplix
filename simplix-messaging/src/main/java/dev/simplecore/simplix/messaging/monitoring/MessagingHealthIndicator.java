package dev.simplecore.simplix.messaging.monitoring;

import dev.simplecore.simplix.messaging.broker.BrokerCapabilities;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Spring Boot Actuator health indicator for the messaging broker.
 *
 * <p>Reports the broker as UP when {@link BrokerStrategy#isReady()} returns {@code true},
 * and DOWN otherwise. Includes broker name and capability details.
 */
@Slf4j
public class MessagingHealthIndicator implements HealthIndicator {

    private final BrokerStrategy brokerStrategy;

    /**
     * Create a new health indicator.
     *
     * @param brokerStrategy the active broker strategy to monitor
     */
    public MessagingHealthIndicator(BrokerStrategy brokerStrategy) {
        this.brokerStrategy = brokerStrategy;
    }

    @Override
    public Health health() {
        try {
            boolean ready = brokerStrategy.isReady();
            BrokerCapabilities capabilities = brokerStrategy.capabilities();

            Health.Builder builder = ready ? Health.up() : Health.down();
            builder.withDetail("broker", brokerStrategy.name())
                    .withDetail("consumerGroups", capabilities.consumerGroups())
                    .withDetail("replay", capabilities.replay())
                    .withDetail("ordering", capabilities.ordering())
                    .withDetail("deadLetter", capabilities.deadLetter());

            return builder.build();
        } catch (Exception e) {
            log.error("Health check failed for messaging broker", e);
            return Health.down()
                    .withDetail("broker", brokerStrategy.name())
                    .withException(e)
                    .build();
        }
    }
}
