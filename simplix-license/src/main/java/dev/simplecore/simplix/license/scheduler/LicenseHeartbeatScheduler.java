package dev.simplecore.simplix.license.scheduler;

import dev.simplecore.simplix.license.activation.LicenseActivationService;
import dev.simplecore.simplix.license.config.LicenseProperties;
import dev.simplecore.simplix.license.core.LicenseManager;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationError;
import dev.accesscore.license.sdk.protocol.ActivationModel.ActivationOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Refreshes the license token on the schedule the license asks for.
 *
 * <p>The heartbeat is what carries revocation, expiry, and contract changes to an online
 * deployment: the server answers with a freshly signed token, so nothing has to be pushed. A
 * closed-network deployment has no heartbeat requirement in its license and is skipped.
 *
 * <p>The task runs often and does nothing most times — the interval the license specifies
 * decides when a refresh is actually attempted, so the schedule here only needs to be finer
 * than the shortest interval in use.
 */
public class LicenseHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(LicenseHeartbeatScheduler.class);

    /** One hour, as a literal because scheduling annotations need a constant expression. */
    private static final long CHECK_INTERVAL_MS = 3_600_000L;

    private final LicenseActivationService activationService;
    private final LicenseManager licenseManager;
    private final LicenseProperties properties;

    /**
     * @param activationService what refreshes the token
     * @param licenseManager the source of the current judgement
     * @param properties the license configuration
     */
    public LicenseHeartbeatScheduler(LicenseActivationService activationService,
                                     LicenseManager licenseManager,
                                     LicenseProperties properties) {
        this.activationService = activationService;
        this.licenseManager = licenseManager;
        this.properties = properties;
    }

    /**
     * Refreshes the token when the license asks for one and the interval has elapsed.
     */
    @Scheduled(fixedDelay = CHECK_INTERVAL_MS, initialDelay = CHECK_INTERVAL_MS)
    public void heartbeatIfDue() {
        if (!properties.getActivation().isHeartbeatEnabled() || !isDue()) {
            return;
        }

        ActivationOutcome outcome = activationService.heartbeat();
        if (outcome.isSuccess()) {
            log.info("License heartbeat succeeded");
            return;
        }
        if (outcome.error() == ActivationError.SERVER_UNREACHABLE) {
            log.warn("License heartbeat could not reach the server; will retry");
            return;
        }
        log.warn("License heartbeat failed: {}", outcome.error());
    }

    /**
     * @return whether the license requires a heartbeat and the interval has elapsed
     */
    private boolean isDue() {
        LicensePayload payload = licenseManager.getState().payload();
        if (payload == null || !Boolean.TRUE.equals(payload.heartbeatRequired())) {
            return false;
        }
        Optional<RegistrationRecord> record = licenseManager.currentRecord();
        if (record.isEmpty() || !record.get().hasActivationCredentials()) {
            return false;
        }

        Instant last = record.get().lastHeartbeatAt();
        if (last == null) {
            return true;
        }
        int interval = payload.heartbeatIntervalDays() == null
                ? 1
                : Math.max(1, payload.heartbeatIntervalDays());
        return Instant.now().isAfter(last.plus(Duration.ofDays(interval)));
    }
}
