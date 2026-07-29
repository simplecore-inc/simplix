package dev.simplecore.simplix.license.core;

import dev.simplecore.simplix.license.config.LicenseProperties;
import dev.simplecore.simplix.license.integrity.RuntimeIntegrityChecker;
import dev.simplecore.simplix.license.model.LicenseState;
import dev.accesscore.license.sdk.model.LicenseModel.EvaluationResponse;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.model.LicenseStatus;
import dev.accesscore.license.sdk.spi.LicenseSpi.LifecycleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * What this deployment does about its license.
 *
 * <p>The judgement is the SDK's — this asks it, publishes the answer to the shared state every
 * gate reads, and owns the parts the SDK deliberately does not: when verification runs, where a
 * hand-provisioned token is picked up, and what a deployment does when the registration it
 * holds turns out to be unusable.
 *
 * <p>Enforcement is unconditional. No configuration turns it off, so an unlicensed deployment
 * is one with no usable features rather than one with a bypass flag set. Development runs the
 * same path with a development-channel license.
 */
public class LicenseManager {

    private static final Logger log = LoggerFactory.getLogger(LicenseManager.class);

    private final LicenseProperties properties;
    // The SDK's manager shares this class's simple name; it is named in full here because both
    // are needed in one file, and this is the only line that mentions it.
    private final dev.accesscore.license.sdk.LicenseManager sdk;
    private final RuntimeIntegrityChecker integrityChecker;
    private final LicenseState state;
    private final LicenseAuditTrail auditTrail;
    private final String publicKeyFingerprint;

    /**
     * @param properties the license configuration
     * @param sdk the SDK manager holding the store, the keys, and the judgement
     * @param integrityChecker the binary checksum verifier
     * @param state the shared runtime state every gate reads
     * @param auditTrail where status transitions are recorded
     * @param publicKeyFingerprint the fingerprint of the verification key this build carries
     */
    public LicenseManager(LicenseProperties properties,
                          dev.accesscore.license.sdk.LicenseManager sdk,
                          RuntimeIntegrityChecker integrityChecker,
                          LicenseState state,
                          LicenseAuditTrail auditTrail,
                          String publicKeyFingerprint) {
        this.properties = properties;
        this.sdk = sdk;
        this.integrityChecker = integrityChecker;
        this.state = state;
        this.auditTrail = auditTrail;
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    /**
     * Imports a hand-provisioned token when the store holds none, then judges.
     *
     * <p>The key this deployment verifies against is announced first, so the log records which
     * license server it can accept tokens from before it records what it made of the token it
     * holds. Without that line a token signed by the wrong key looks indistinguishable from a
     * corrupted one.
     *
     * <p>Verification runs before the recovery attempt, because whether the provisioned file is
     * even considered depends on what the stored registration turned out to be worth.
     */
    public void initialize() {
        log.info("License public key fingerprint: {}. Only licenses signed by the matching "
                        + "private key can be registered here; compare this with the fingerprint "
                        + "the license server logs for its signing key.", publicKeyFingerprint);
        importProvisionedToken();
        verify();
        recoverFromProvisionedToken();
    }

    /**
     * Judges the stored registration and publishes the result.
     */
    public void verify() {
        LicenseStatus previous = state.status();
        Instant now = Instant.now();

        EvaluationResponse evaluation = sdk.verify(now, integrityChecker.verify());
        state.markChecked(now);

        if (previous != evaluation.status()) {
            auditTrail.statusChanged(previous, evaluation.status(),
                    evaluation.payload() != null ? evaluation.payload().licenseId() : null);
        }
        if (evaluation.usable()) {
            log.info("License verified: status={}, product={}, expires={}", evaluation.status(),
                    evaluation.payload() != null ? evaluation.payload().productCode() : null,
                    evaluation.payload() != null ? evaluation.payload().expiresAt() : null);
        }
    }

    /**
     * Replaces the stored registration and judges it, so a caller that obtains a new token
     * never has to reason about runtime state itself.
     *
     * @param record the registration to persist
     */
    public void applyRecord(RegistrationRecord record) {
        sdk.apply(record, Instant.now(), integrityChecker.verify());
        state.markChecked(Instant.now());
        verify();
    }

    /**
     * Records that the license server revoked this activation and judges again. The stamp is
     * persisted so the denial survives a restart.
     */
    public void markRevoked() {
        sdk.markRevoked(Instant.now(), integrityChecker.verify());
        verify();
    }

    /**
     * Discards the registered license, returning the deployment to its unregistered state.
     */
    public void clearRegistration() {
        sdk.clearRegistration(Instant.now(), integrityChecker.verify());
        verify();
    }

    /**
     * @return the persisted registration, or empty when nothing is registered
     */
    public Optional<RegistrationRecord> currentRecord() {
        return sdk.currentRecord();
    }

    /**
     * @return the current license state holder
     */
    public LicenseState getState() {
        return state;
    }

    /**
     * @return what this machine reports about itself, empty when no stable identifier could be
     *         read
     */
    public List<String> machineFingerprints() {
        return sdk.fingerprint().fingerprintsOrEmpty();
    }

    /**
     * @return the fingerprint of the license public key compiled into this deployment, which
     *         must match the one the issuing license server reports for its signing key
     */
    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    /**
     * @param feature the license feature key
     * @return whether the current license grants the feature
     */
    public boolean isFeatureAvailable(String feature) {
        return state.gate().isLicensed(feature);
    }

    /**
     * The modules the current license grants.
     *
     * <p>A deployment running above its entitled release keeps working but grants nothing: the
     * ceiling was sold, and an upgrade past it is not a licence to keep the paid modules. The
     * core decides that once, and every gate reads this list.
     *
     * @return the features the current license grants, empty when it grants none
     */
    public List<String> availableFeatures() {
        return state.features();
    }

    /**
     * @return whether mutations are blocked because an expired license is in its grace period
     */
    public boolean isGracePeriodReadOnly() {
        return state.isGracePeriod()
                && properties.getGracePeriodMode() == LicenseProperties.GracePeriodMode.READ_ONLY;
    }

    /**
     * Imports a token placed at the configured path when the store holds none, so a
     * development or closed-network deployment can be provisioned by dropping in a file.
     */
    private void importProvisionedToken() {
        Optional<RegistrationRecord> existing = sdk.currentRecord();
        if (existing.isPresent() && existing.get().hasToken()) {
            return;
        }
        readProvisionedToken().ifPresent(token -> {
            sdk.apply(RegistrationRecord.ofToken(token), Instant.now(), true);
            log.info("Imported the license token provisioned at {}", properties.getTokenPath());
        });
    }

    /**
     * Replaces a stored registration that turned out to be unusable with the provisioned token,
     * when the file holds a different one.
     *
     * <p>Refusing to look at the file while any token is stored costs a deployment its only
     * recovery route that needs no shell: a registration that stops verifying — a license past
     * its grace period, one written in a schema this release no longer reads — cannot be
     * replaced by dropping in a new file, because the dead one keeps the slot.
     *
     * <p>The overwrite is therefore narrow: it happens only when the stored registration is
     * already refused, so a working registration is never displaced by a stale file left on
     * disk. A revoked activation is excluded outright — the offline registration path refuses
     * to reinstate one from a file for the same reason, and revocation has to outlive a file
     * drop to mean anything.
     *
     * <p>The verification watermark carries over. It records how far this host's clock has been
     * seen to advance, which is a fact about the host rather than about the registration, and
     * dropping it would let a rolled-back clock pass unnoticed on the next start.
     */
    private void recoverFromProvisionedToken() {
        LicenseStatus refused = state.status();
        if (state.isUsable() || refused == LicenseStatus.REVOKED) {
            return;
        }

        Optional<RegistrationRecord> existing = sdk.currentRecord();
        if (existing.isEmpty() || !existing.get().hasToken()) {
            return;
        }

        Optional<String> provisioned = readProvisionedToken();
        if (provisioned.isEmpty() || provisioned.get().equals(existing.get().licenseToken())) {
            return;
        }

        applyRecord(RegistrationRecord.ofToken(provisioned.get())
                .withWatermark(existing.get().verificationWatermark()));
        log.info("Stored registration was refused as {}; re-registered from the token "
                + "provisioned at {}", refused, properties.getTokenPath());

        if (state.isUsable()) {
            LicensePayload recovered = state.payload();
            auditTrail.lifecycle(LifecycleEvent.ACTIVATED,
                    recovered != null ? recovered.licenseId() : null,
                    "Re-registered from the provisioned token file, replacing a registration "
                            + "refused as " + refused);
        } else {
            log.error("The provisioned token was refused as {} as well; this deployment stays "
                    + "closed", state.status());
        }
    }

    /**
     * @return the token placed at the configured path, empty when there is no readable one
     */
    private Optional<String> readProvisionedToken() {
        Path tokenPath = Path.of(properties.getTokenPath());
        if (!Files.exists(tokenPath)) {
            return Optional.empty();
        }
        try {
            String token = Files.readString(tokenPath, StandardCharsets.UTF_8).trim();
            return token.isEmpty() ? Optional.empty() : Optional.of(token);
        } catch (IOException e) {
            log.error("Failed to read the license token at {}", tokenPath, e);
            return Optional.empty();
        }
    }
}
