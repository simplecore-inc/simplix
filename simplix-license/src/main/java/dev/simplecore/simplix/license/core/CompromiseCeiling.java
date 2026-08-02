package dev.simplecore.simplix.license.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.accesscore.license.sdk.issuing.ReleaseVersions;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What this deployment held when it first learned that one of its verification keys had been
 * compromised.
 *
 * <p>A token signed by such a key may keep the deployment where it is; it may not take it
 * further. The comparison is against this fixed record rather than against the live state,
 * because a ceiling measured against what the deployment currently holds rises with every token
 * that raises it and therefore stops nothing.
 *
 * <p>A deployment holding nothing pins nothing, and then a token signed by that key grants
 * nothing either. That is what closes the other route: standing up a fresh deployment and
 * feeding it a forged token gains a forger no more than the empty deployment already had.
 *
 * @param pinnedAt when the ceiling was fixed
 * @param licensed whether a usable license was held at that moment; false means the ceiling is
 *        nothing at all, not an unrestricted one
 * @param features the feature keys held then
 * @param limits the ceiling per quota code held then; an absent code was unlimited
 * @param expiresAt when what was held then stopped being valid, null for one that never does
 * @param maxRelease the highest release entitled then, null for one entitled to any
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompromiseCeiling(
        Instant pinnedAt,
        boolean licensed,
        List<String> features,
        Map<String, Long> limits,
        Instant expiresAt,
        String maxRelease
) {

    /**
     * @param pinnedAt when the ceiling was fixed
     * @return the ceiling of a deployment that held nothing
     */
    public static CompromiseCeiling nothing(Instant pinnedAt) {
        return new CompromiseCeiling(pinnedAt, false, List.of(), Map.of(), null, null);
    }

    /**
     * @param pinnedAt when the ceiling was fixed
     * @param payload what the deployment held then, null when it held nothing usable
     * @return the ceiling that payload fixes
     */
    public static CompromiseCeiling from(Instant pinnedAt, LicensePayload payload) {
        if (payload == null) {
            return nothing(pinnedAt);
        }
        return new CompromiseCeiling(
                pinnedAt,
                true,
                payload.features() == null ? List.of() : List.copyOf(payload.features()),
                payload.limits() == null ? Map.of() : Map.copyOf(payload.limits()),
                payload.expiresAt(),
                payload.maxRelease());
    }

    /**
     * Whether a payload asks for no more than what was held.
     *
     * <p>Each axis is compared on its own, and one of them rising refuses the whole token.
     * Granting the parts that fit and dropping the rest would leave nobody able to say what the
     * deployment is running under.
     *
     * <p>The release ceiling is one of those axes because it is also something that was sold:
     * raising it hands over a newer edition nobody bought, and the deployment already refuses
     * such a release on its own.
     *
     * @param candidate the payload a token carries
     * @param releases how two release versions are ranked
     * @return whether it stays inside this ceiling
     */
    public boolean covers(LicensePayload candidate, ReleaseVersions releases) {
        if (!licensed || candidate == null) {
            return false;
        }
        return coversFeatures(candidate)
                && coversLimits(candidate)
                && coversExpiry(candidate)
                && coversRelease(candidate, releases);
    }

    /**
     * @param candidate the payload a token carries
     * @return whether it names no feature that was not held
     */
    private boolean coversFeatures(LicensePayload candidate) {
        if (candidate.features() == null) {
            return true;
        }
        return features.containsAll(candidate.features());
    }

    /**
     * Whether every ceiling that was capped then is still capped at or below that value.
     *
     * <p>A code absent from the candidate is unlimited rather than zero, so a token that simply
     * drops a capped code asks for more than was held and is refused with the rest.
     *
     * @param candidate the payload a token carries
     * @return whether no quota rose
     */
    private boolean coversLimits(LicensePayload candidate) {
        for (Map.Entry<String, Long> pinned : limits.entrySet()) {
            if (pinned.getValue() == null) {
                continue;
            }
            Long offered = candidate.limitFor(pinned.getKey());
            if (offered == null || offered > pinned.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param candidate the payload a token carries
     * @return whether it expires no later than what was held
     */
    private boolean coversExpiry(LicensePayload candidate) {
        if (expiresAt == null) {
            return true;
        }
        return candidate.expiresAt() != null && !candidate.expiresAt().isAfter(expiresAt);
    }

    /**
     * Whether a candidate is entitled to no newer a release than what was held.
     *
     * <p>An absent ceiling is entitlement to any release rather than to none, so a token that
     * simply drops the ceiling that was pinned asks for more than was held and is refused with
     * the rest — the same reading a dropped quota cap gets.
     *
     * <p>The ranking crosses to the SDK because that is the only place that knows one: compared
     * as text, {@code 3.10} ranks below {@code 3.9} and a token raising the ceiling by a minor
     * version would pass.
     *
     * @param candidate the payload a token carries
     * @param releases how two release versions are ranked
     * @return whether the release ceiling did not rise
     */
    private boolean coversRelease(LicensePayload candidate, ReleaseVersions releases) {
        if (maxRelease == null || maxRelease.isBlank()) {
            return true;
        }
        String offered = candidate.maxRelease();
        if (offered == null || offered.isBlank()) {
            return false;
        }
        return releases.isWithinCeiling(offered, maxRelease);
    }
}
