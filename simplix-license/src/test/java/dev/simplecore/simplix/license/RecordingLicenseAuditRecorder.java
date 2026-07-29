package dev.simplecore.simplix.license;

import dev.accesscore.license.sdk.model.LicenseStatus;
import dev.accesscore.license.sdk.spi.LicenseSpi.AuditRecorder;
import dev.accesscore.license.sdk.spi.LicenseSpi.LifecycleEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * An audit recorder that keeps what it was told, so a test can assert what licensing recorded.
 */
public class RecordingLicenseAuditRecorder implements AuditRecorder {

    /** Every status transition recorded, as {@code PREVIOUS->CURRENT}. */
    public final List<String> statusChanges = new ArrayList<>();

    /** Every lifecycle event recorded. */
    public final List<LifecycleEvent> lifecycleEvents = new ArrayList<>();

    /** Every ceiling refusal recorded, as {@code CODE:current/limit}. */
    public final List<String> limitsReached = new ArrayList<>();

    @Override
    public void recordStatusChange(LicenseStatus previous, LicenseStatus current,
                                   String licenseId) {
        statusChanges.add(previous + "->" + current);
    }

    @Override
    public void recordLifecycle(LifecycleEvent event, String licenseId, String detail) {
        lifecycleEvents.add(event);
    }

    @Override
    public void recordLimitReached(String quotaCode, long current, long limit) {
        limitsReached.add(quotaCode + ":" + current + "/" + limit);
    }
}
