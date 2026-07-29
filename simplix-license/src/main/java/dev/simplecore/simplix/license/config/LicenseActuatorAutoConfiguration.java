package dev.simplecore.simplix.license.config;

import dev.simplecore.simplix.license.health.LicenseHealthIndicator;
import dev.simplecore.simplix.license.health.LicenseMetrics;
import dev.simplecore.simplix.license.model.LicenseState;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator wiring for license health and metrics.
 *
 * <p>Separated from {@link LicenseAutoConfiguration} so licensing works without Actuator on the
 * classpath. The inner conditions on {@link LicenseState} are a safety net: even if component
 * scanning bypassed the outer condition, no bean is created without the licensing core.
 */
@AutoConfiguration(after = LicenseAutoConfiguration.class)
@ConditionalOnBean(LicenseState.class)
public class LicenseActuatorAutoConfiguration {

    /** Health, when Actuator is present. */
    @Configuration
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnBean(LicenseState.class)
    static class HealthIndicatorConfiguration {

        /**
         * @param licenseState the shared state the indicator reads
         * @return the license health indicator
         */
        @Bean
        public LicenseHealthIndicator licenseHealthIndicator(LicenseState licenseState) {
            return new LicenseHealthIndicator(licenseState);
        }
    }

    /** Metrics, when Micrometer is present. */
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(LicenseState.class)
    static class MetricsConfiguration {

        /**
         * @param registry where the gauges are registered
         * @param licenseState the shared state they read
         * @return the license gauges
         */
        @Bean
        public LicenseMetrics licenseMetrics(MeterRegistry registry, LicenseState licenseState) {
            return new LicenseMetrics(registry, licenseState);
        }
    }
}
