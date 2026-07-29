package dev.simplecore.simplix.license.model;

import dev.accesscore.license.sdk.gate.LicenseGate;
import dev.accesscore.license.sdk.model.LicenseModel.EvaluationResponse;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.LicenseStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This deployment's view of what the license core last concluded.
 *
 * <p>The judgement itself lives in the SDK's gate, which is the one cache every enforcement
 * point reads. This class is the product's reading of it: the questions this application asks —
 * is it usable, what does it grant, when was it last checked — answered from that one place, so
 * a filter, an aspect, a scheduler, and the health indicator cannot disagree.
 *
 * <p>The instant of the last check is held here rather than in the gate because it is a fact
 * about this deployment's schedule, not about the license.
 */
public class LicenseState {

    private final LicenseGate gate;
    private final AtomicReference<Instant> lastChecked;

    /**
     * @param gate the SDK gate holding the judgement
     */
    public LicenseState(LicenseGate gate) {
        this.gate = gate;
        this.lastChecked = new AtomicReference<>(Instant.now());
    }

    /**
     * Records that a fresh judgement was published.
     *
     * @param checkedAt when it was reached
     */
    public void markChecked(Instant checkedAt) {
        lastChecked.set(checkedAt);
    }

    /**
     * @return the gate every request-time check reads
     */
    public LicenseGate gate() {
        return gate;
    }

    /**
     * @return the current judgement, and when it was reached
     */
    public Snapshot snapshot() {
        EvaluationResponse evaluation = gate.state();
        return new Snapshot(evaluation.status(), evaluation.payload(), lastChecked.get(),
                evaluation);
    }

    /**
     * @return what the last judgement concluded
     */
    public LicenseStatus status() {
        return gate.status();
    }

    /**
     * @return the payload the status was judged from, null when none could be read
     */
    public LicensePayload payload() {
        return gate.state().payload();
    }

    /**
     * @return whether every check passed
     */
    public boolean isValid() {
        return status() == LicenseStatus.VALID;
    }

    /**
     * @return whether the license has expired but is still inside its grace period
     */
    public boolean isGracePeriod() {
        return status() == LicenseStatus.GRACE_PERIOD;
    }

    /**
     * Whether the deployment keeps operating.
     *
     * <p>A release above the entitled ceiling stays usable on purpose. The ceiling is an
     * entitlement control, and treating an upgrade as an unlicensed deployment would stop the
     * enforcement filter and leave people standing at locked doors. What it costs is the paid
     * modules: the available-feature list is empty in that state.
     *
     * @return whether the deployment keeps operating
     */
    public boolean isUsable() {
        return gate.isUsable();
    }

    /**
     * @param feature the license feature key
     * @return whether the current license grants it
     */
    public boolean hasFeature(String feature) {
        return gate.isLicensed(feature);
    }

    /**
     * @return the commercial bundle name the contract carries, null when none was sold; a
     *         display value that no gate ever reads
     */
    public String tierLabel() {
        LicensePayload payload = payload();
        return payload != null ? payload.tierLabel() : null;
    }

    /**
     * @return the feature keys the current license grants, empty when it grants none
     */
    public List<String> features() {
        List<String> available = gate.state().availableFeatures();
        return available != null ? available : Collections.emptyList();
    }

    /**
     * What one judgement concluded, and when.
     *
     * @param status what the judgement concluded
     * @param payload the payload it was judged from, null when none could be read
     * @param lastChecked when it was reached
     * @param evaluation the whole judgement, for a caller that needs its denial or its limits
     */
    public record Snapshot(
            LicenseStatus status,
            LicensePayload payload,
            Instant lastChecked,
            EvaluationResponse evaluation
    ) {
    }
}
