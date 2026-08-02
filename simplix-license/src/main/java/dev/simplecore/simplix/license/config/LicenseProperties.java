package dev.simplecore.simplix.license.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe configuration properties for the license system.
 *
 * <p>There is deliberately no property that switches enforcement off, and no property that
 * points at different verification keys: both would hand a customer-controlled bypass to an
 * on-premise deployment. The verification keys are fixed in the binary, and a development
 * deployment runs the same verification path with a DEV-channel license.
 *
 * <pre>{@code
 * application:
 *   license:
 *     token-path: ./license.key
 *     state-path: ./license-state.json
 *     re-verification-interval-minutes: 30
 *     grace-period-mode: READ_ONLY
 *     app-version: 0.0.1
 *     activation:
 *       server-url: https://license.accesscore.dev
 *       heartbeat-enabled: true
 * }</pre>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.license")
public class LicenseProperties {

    /**
     * Classpath location of the keys licenses are verified against, each with its name and
     * whether its signing half is known to be compromised. Fixed rather than configurable so a
     * deployment cannot verify against keys of its own making, and one resource rather than a
     * name in code beside a key in a file so the two cannot drift apart.
     */
    public static final String VERIFICATION_KEYS_LOCATION =
            "classpath:license-verification-keys.json";

    /**
     * Path a license token may be provisioned at by hand. Read at startup and imported into
     * the durable store; this is how development and closed-network installations get their
     * token.
     */
    private String tokenPath = "./license.key";

    /** Path the file-backed store keeps mutable license state at. */
    private String statePath = "./license-state.json";

    /** Interval in minutes for periodic license re-verification. */
    @Min(1)
    private int reVerificationIntervalMinutes = 30;

    /** Behavior during the grace period after license expiry. */
    private GracePeriodMode gracePeriodMode = GracePeriodMode.READ_ONLY;

    /**
     * The product family this deployment's keys are issued under.
     *
     * <p>Part of a product key's checksum, so a key issued under another family does not
     * validate here. It names what this product is sold as, which is a fact about the product
     * rather than something the SDK could know.
     */
    private String productKeyPrefix = "ACPS";

    /** Online activation settings. */
    private Activation activation = new Activation();

    public enum GracePeriodMode {
        /** Allow only GET requests during grace period. */
        READ_ONLY,
        /** Allow all requests but with feature restrictions. */
        RESTRICTED
    }

    @Getter
    @Setter
    public static class Activation {

        /** Activation server URL. When empty, only file-provisioned licenses work. */
        private String serverUrl;

        /** Whether the deployment sends periodic heartbeats when the license asks for them. */
        private boolean heartbeatEnabled = true;
    }
}
