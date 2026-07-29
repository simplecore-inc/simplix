package dev.simplecore.simplix.license.enforcement;

import dev.accesscore.license.sdk.gate.LicenseGate;

import java.util.List;

/**
 * The single place a feature's availability is judged.
 *
 * <p>A feature is available when the license grants it <em>and</em> an administrator has
 * activated it. REST endpoints reach this through the gate aspect; schedulers, listeners, and
 * the approval dispatch call it directly. Keeping one judgement is what stops a background job
 * from running for a feature the administrator switched off.
 *
 * <p>Both halves of that judgement are the SDK gate's, so this deployment and any other running
 * the same license answer alike.
 */
public class FeatureAccess {

    private final LicenseGate gate;

    /**
     * @param gate the gate every enforcement point reads
     */
    public FeatureAccess(LicenseGate gate) {
        this.gate = gate;
    }

    /**
     * @param feature the license feature key
     * @return whether the license grants the feature and it is activated
     */
    public boolean isAllowed(String feature) {
        return gate.isAllowed(feature);
    }

    /**
     * @param feature the license feature key
     * @return whether the license grants the feature, ignoring activation
     */
    public boolean isLicensed(String feature) {
        return gate.isLicensed(feature);
    }

    /**
     * @param feature the license feature key
     * @return whether an administrator has activated the feature
     */
    public boolean isActivated(String feature) {
        return gate.isActivated(feature);
    }

    /**
     * The features a client may act on: granted by the license and activated. The frontend
     * gates its navigation on this list, so it has to agree with the server-side gates.
     *
     * @return the effectively available feature keys
     */
    public List<String> effectiveFeatures() {
        return gate.effectiveFeatures();
    }
}
