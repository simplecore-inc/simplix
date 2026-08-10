package dev.simplecore.simplix.license.controller.dto;

import dev.accesscore.license.sdk.model.LicenseChannel;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTOs for the license surface. Grouped in a feature container because this surface has no
 * backing entity — the license lives in a signed token, not in a table the generator knows.
 */
public class LicenseDTOs {

    /**
     * What the license screen shows: the runtime verdict plus the contract behind it.
     * Absent when nothing is registered, which the status field states on its own.
     */
    @Getter
    @Builder
    public static class LicenseStatusDTO {
        private String status;
        private Instant lastChecked;
        private Boolean usable;
        private Boolean onlineActivationAvailable;
        /**
         * Every fingerprint this machine reports about itself. The license binds to all of
         * them, and the deployment later matches on whatever it still reads — which is what
         * lets a container that lost a mount keep working.
         */
        private List<String> machineFingerprints;

        private Boolean machineFingerprintAvailable;

        /**
         * Which kinds of identifier answered, and which were consulted and gave nothing.
         *
         * <p>Reported because the fingerprints alone cannot be acted on. A deployment whose
         * license is refused as belonging to another machine is looking at two opaque digests
         * and has no way to tell a lost mount from a token cut for a different host — the two
         * demand opposite fixes, and the screen says the same sentence for both. What was read
         * and what came back empty is the difference, and the SDK collects it already.
         *
         * <p>They name a KIND rather than a path — {@code IO_PLATFORM_UUID}, and its siblings on
         * other systems — so this tells an operator what to mount without publishing a recipe
         * for forging one. What a container has to be handed is in the deployment's own compose
         * file either way; what it actually received is only here.
         */
        private List<String> machineIdentifierSources;

        /** The kinds consulted that gave nothing, which is what an operator can still mount. */
        private List<String> machineIdentifierSourcesUnavailable;

        /**
         * Identifies the license key pair this deployment accepts tokens from. It is reported
         * so an operator can compare it with the fingerprint the license server logs for its
         * signing key: two different values are why an otherwise correct license is refused as
         * unverifiable. It is a digest of the public key already shipped in the binary, so it
         * reveals nothing.
         */
        private String publicKeyFingerprint;

        /**
         * The registered product key with its middle groups withheld, so an operator can tell
         * which key this deployment runs on without the screen carrying a value that would
         * authenticate an activation request. Present whenever a key is registered, including
         * while the token itself cannot be read.
         */
        private String maskedProductKey;

        private String licenseId;
        private String activationId;
        private String customer;
        private LicenseChannel channel;
        private String productCode;
        private String maxRelease;
        private String tierLabel;
        private Instant issuedAt;
        private Instant activatedAt;
        private Instant expiresAt;
        private Instant graceExpiresAt;
        private Integer gracePeriodDays;
        private Map<String, Long> limits;
        private Map<String, Long> usage;
        private List<String> features;
        private Boolean heartbeatRequired;
        private Instant lastHeartbeatAt;
    }

    /**
     * Registers a license by claiming a seat over the network.
     */
    @Getter
    @Setter
    public static class ActivateRequestDTO {
        @NotBlank
        private String productKey;

        /**
         * Whether a license that still works may be replaced.
         *
         * <p>Absent by default so registering a second key over a working one is a decision
         * rather than an accident: the seat the current key holds stays occupied on the license
         * server, which nothing on this side can release once the key is gone.
         */
        private Boolean replaceExisting;
    }

    /**
     * Points this deployment at the license server it activates against.
     */
    @Getter
    @Setter
    public static class LicenseServerRequestDTO {
        @NotBlank
        private String serverUrl;
    }

    /**
     * Registers a license from a signed response file produced for an offline request.
     */
    @Getter
    @Setter
    public static class OfflineResponseDTO {
        @NotBlank
        private String productKey;

        @NotBlank
        private String licenseToken;

        /** Whether a license that still works may be replaced; see {@link ActivateRequestDTO}. */
        private Boolean replaceExisting;
    }

    /**
     * The activation request an operator carries to the license server when the deployment
     * has no network path to it.
     */
    @Getter
    @Builder
    public static class ActivationRequestFileDTO {

        /**
         * The request body exactly as this deployment would have sent it.
         *
         * <p>Carried whole rather than field by field: the issuing side reads the same bytes
         * whether they arrived over a socket or on a memory stick, so an offline activation
         * goes through the identical path an online one does. Splitting it into fields here
         * would let the two drift.
         */
        private String body;

        /** The value the issued token has to echo, kept so a replayed answer is detectable. */
        private String nonce;

        /** When this deployment created the request. */
        private Instant requestedAt;
    }
}
