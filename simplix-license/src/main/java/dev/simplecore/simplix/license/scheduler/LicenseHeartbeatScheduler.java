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

import java.time.Clock;
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
 * than the shortest interval in use. That schedule is
 * {@code application.license.activation.heartbeat-check-interval}; after a failed refresh the
 * next attempt waits {@code heartbeat-retry-interval} from the failure instead, so a deployment
 * the server keeps refusing does not ask on every check.
 */
public class LicenseHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(LicenseHeartbeatScheduler.class);

    /**
     * The interval, in hours, used when the license carries none. The SDK reads a zero interval
     * the same way, so the deadline it judges and the schedule kept here agree.
     */
    private static final int DEFAULT_INTERVAL_HOURS = 1;

    private final LicenseActivationService activationService;
    private final LicenseManager licenseManager;
    private final LicenseProperties properties;
    private final Clock clock;

    /** When the last refresh failed, cleared by a success. Held in memory only. */
    private volatile Instant lastFailureAt;

    /**
     * @param activationService what refreshes the token
     * @param licenseManager the source of the current judgement
     * @param properties the license configuration
     */
    public LicenseHeartbeatScheduler(LicenseActivationService activationService,
                                     LicenseManager licenseManager,
                                     LicenseProperties properties) {
        this(activationService, licenseManager, properties, Clock.systemUTC());
    }

    /**
     * @param activationService what refreshes the token
     * @param licenseManager the source of the current judgement
     * @param properties the license configuration
     * @param clock what dates the interval and the retry wait
     */
    LicenseHeartbeatScheduler(LicenseActivationService activationService,
                              LicenseManager licenseManager,
                              LicenseProperties properties,
                              Clock clock) {
        this.activationService = activationService;
        this.licenseManager = licenseManager;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Refreshes the token when the license asks for one and the interval has elapsed.
     */
    @Scheduled(
            fixedDelayString = "${application.license.activation.heartbeat-check-interval:5m}",
            initialDelayString = "${application.license.activation.heartbeat-check-interval:5m}")
    public void heartbeatIfDue() {
        if (!properties.getActivation().isHeartbeatEnabled() || !isDue() || isWaitingToRetry()) {
            return;
        }

        ActivationOutcome outcome = activationService.heartbeat();
        if (outcome.isSuccess()) {
            lastFailureAt = null;
            log.info("License heartbeat succeeded");
            return;
        }
        lastFailureAt = clock.instant();
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
        Integer signed = payload.heartbeatIntervalHours();
        int interval = signed == null || signed <= 0 ? DEFAULT_INTERVAL_HOURS : signed;
        return clock.instant().isAfter(last.plus(Duration.ofHours(interval)));
    }

    /**
     * @return whether a refresh failed less than the retry interval ago
     */
    private boolean isWaitingToRetry() {
        Instant failedAt = lastFailureAt;
        if (failedAt == null) {
            return false;
        }
        Duration retry = properties.getActivation().getHeartbeatRetryInterval();
        return clock.instant().isBefore(failedAt.plus(retry));
    }
}
