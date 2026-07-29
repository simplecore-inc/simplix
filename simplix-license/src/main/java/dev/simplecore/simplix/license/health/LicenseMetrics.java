package dev.simplecore.simplix.license.health;

import dev.simplecore.simplix.license.model.LicenseState;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.LicenseStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.time.Instant;

/**
 * Micrometer gauges for the license.
 *
 * <ul>
 *   <li>{@code license.valid} — 1 while the deployment keeps operating, 0 otherwise</li>
 *   <li>{@code license.days.remaining} — days until expiry, negative once past it</li>
 *   <li>{@code license.grace.period} — 1 while inside the grace period, 0 otherwise</li>
 * </ul>
 */
public class LicenseMetrics {

    /**
     * @param registry where the gauges are registered
     * @param licenseState the shared state they read
     */
    public LicenseMetrics(MeterRegistry registry, LicenseState licenseState) {
        Gauge.builder("license.valid", licenseState, state -> state.isUsable() ? 1.0 : 0.0)
                .description("Whether the license currently lets this deployment operate")
                .register(registry);

        Gauge.builder("license.days.remaining", licenseState, state -> {
                    LicensePayload payload = state.payload();
                    if (payload == null || payload.expiresAt() == null) {
                        return -1.0;
                    }
                    return Duration.between(Instant.now(), payload.expiresAt()).toDays();
                })
                .description("Days remaining until license expiry")
                .register(registry);

        Gauge.builder("license.grace.period", licenseState, state ->
                        state.status() == LicenseStatus.GRACE_PERIOD ? 1.0 : 0.0)
                .description("Whether the license is in its grace period")
                .register(registry);
    }
}
