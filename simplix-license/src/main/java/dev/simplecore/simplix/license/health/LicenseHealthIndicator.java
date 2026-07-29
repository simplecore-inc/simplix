package dev.simplecore.simplix.license.health;

import dev.simplecore.simplix.license.model.LicenseState;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.LicenseStatus;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Actuator health indicator that reports license status.
 *
 * <p>Reachable at {@code GET /actuator/health/license}. One of the independent verification
 * points in the enforcement architecture.
 */
public class LicenseHealthIndicator extends AbstractHealthIndicator {

    private static final Status GRACE = new Status("GRACE_PERIOD", "License in grace period");

    private final LicenseState licenseState;

    /**
     * @param licenseState the shared state every enforcement point reads
     */
    public LicenseHealthIndicator(LicenseState licenseState) {
        super("License health check failed");
        this.licenseState = licenseState;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        LicenseState.Snapshot snapshot = licenseState.snapshot();
        LicenseStatus status = snapshot.status();

        switch (status) {
            case VALID -> builder.up();
            case GRACE_PERIOD -> builder.status(GRACE);
            default -> builder.down();
        }

        builder.withDetail("status", status.name());
        builder.withDetail("lastChecked", snapshot.lastChecked().toString());

        LicensePayload payload = snapshot.payload();
        if (payload != null) {
            builder.withDetail("licenseId", payload.licenseId());
            builder.withDetail("productCode", payload.productCode());
            builder.withDetail("tierLabel", payload.tierLabel());
            builder.withDetail("customer", payload.customer());
            builder.withDetail("expiresAt", String.valueOf(payload.expiresAt()));
            builder.withDetail("features", payload.features());
        }
    }
}
