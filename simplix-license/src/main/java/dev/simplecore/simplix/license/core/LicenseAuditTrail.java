package dev.simplecore.simplix.license.core;

import dev.accesscore.license.sdk.model.LicenseStatus;
import dev.accesscore.license.sdk.spi.LicenseSpi.AuditRecorder;
import dev.accesscore.license.sdk.spi.LicenseSpi.LifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.util.function.Consumer;

/**
 * This deployment's way in to its own audit trail.
 *
 * <p>Recording is optional and must never change what the license pipeline concludes. An
 * application that contributes no {@link AuditRecorder} simply records nothing, and a recorder
 * that fails — a database that is down while a scheduled verification runs, for instance — is
 * logged and stepped over. Flipping a deployment's license state because its audit table was
 * unreachable would be the wrong failure.
 */
public class LicenseAuditTrail {

    private static final Logger log = LoggerFactory.getLogger(LicenseAuditTrail.class);

    private final ObjectProvider<AuditRecorder> recorder;

    /**
     * @param recorder the application-contributed recorder, resolved lazily so licensing works
     *        in a context that contributes none
     */
    public LicenseAuditTrail(ObjectProvider<AuditRecorder> recorder) {
        this.recorder = recorder;
    }

    /**
     * Records that a judgement reached a different conclusion than before.
     *
     * @param previous the status before this judgement
     * @param current the status after it
     * @param licenseId the license the status belongs to, may be null
     */
    public void statusChanged(LicenseStatus previous, LicenseStatus current, String licenseId) {
        record(target -> target.recordStatusChange(previous, current, licenseId));
    }

    /**
     * Records a registration lifecycle event.
     *
     * @param event the lifecycle event
     * @param licenseId the license involved, may be null
     * @param detail a short human-readable detail, may be null
     */
    public void lifecycle(LifecycleEvent event, String licenseId, String detail) {
        record(target -> target.recordLifecycle(event, licenseId, detail));
    }

    /**
     * Records an operation refused by a licensed ceiling.
     *
     * @param quotaCode the ceiling that refused the operation
     * @param current how many the deployment already holds
     * @param limit the licensed ceiling
     */
    public void limitReached(String quotaCode, long current, long limit) {
        record(target -> target.recordLimitReached(quotaCode, current, limit));
    }

    /**
     * Runs one recording call, swallowing whatever it throws.
     *
     * <p>The catch is deliberately broad: the recorder is application-supplied, so licensing
     * cannot enumerate the failures its persistence layer raises, and any of them must end the
     * same way — a log line and an unchanged license state.
     *
     * @param action the recording call
     */
    private void record(Consumer<AuditRecorder> action) {
        AuditRecorder target = recorder.getIfAvailable();
        if (target == null) {
            return;
        }
        try {
            action.accept(target);
        } catch (RuntimeException e) {
            log.warn("Failed to record a license event in the audit trail", e);
        }
    }
}
