package dev.simplecore.simplix.license.scheduler;

import dev.simplecore.simplix.license.core.LicenseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Periodic re-verification of the license, so a change made since start is noticed.
 *
 * <p>Re-reads the registration and asks the core again — signature, machine binding, expiry,
 * the clock watermark, and the heartbeat deadline — at a configurable interval.
 *
 * <p>One of the independent verification points in the enforcement architecture.
 */
public class LicenseReVerificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(LicenseReVerificationScheduler.class);

    private final LicenseManager licenseManager;

    /**
     * @param licenseManager what judges the registration
     */
    public LicenseReVerificationScheduler(LicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }

    /**
     * Judges the registration again.
     */
    @Scheduled(
            fixedDelayString = "#{${application.license.re-verification-interval-minutes:30} * 60 * 1000}",
            initialDelayString = "#{${application.license.re-verification-interval-minutes:30} * 60 * 1000}"
    )
    public void reVerify() {
        log.debug("Running scheduled license re-verification");
        licenseManager.verify();
    }
}
