package dev.simplecore.simplix.license.core;

import dev.simplecore.simplix.license.LicenseTestFixture;
import dev.simplecore.simplix.license.config.LicenseProperties;
import dev.simplecore.simplix.license.config.VerificationKey;
import dev.simplecore.simplix.license.config.VerificationKeys;
import dev.simplecore.simplix.license.integrity.RuntimeIntegrityChecker;
import dev.simplecore.simplix.license.model.LicenseState;
import dev.accesscore.license.sdk.gate.LicenseGate;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.model.LicenseStatus;
import dev.accesscore.license.sdk.spi.LicenseSpi.AuditRecorder;
import dev.accesscore.license.sdk.spi.LicenseSpi.LicenseStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a compromised signing key may still do here, and what it may not.
 *
 * <p>The rule under test is one sentence: a token signed by a key whose signing half is in
 * someone else's hands may keep this deployment where it was, and may not take it further. Every
 * case below is that sentence read from one side or the other, and the last one guards the
 * boundary — a key that merely stopped signing is not a key that stopped granting.
 *
 * <p>Nothing here is checkable from a screen. The judgement runs at start and on a schedule, and
 * a ceiling that stopped working would go on saying the licence is fine.
 */
@DisplayName("Compromised signing key - the ceiling on what it may still grant")
class CompromiseCeilingTest {

    private static final String SEATS = LicenseTestFixture.QUOTA_COUNTED;

    @TempDir
    private Path workingDirectory;

    /**
     * The licence this deployment already holds: two modules, ten seats, a year left, and
     * nothing newer than release 1.9.
     *
     * <p>Fixed once per case rather than rebuilt per call. Rebuilt, its expiry would be stamped
     * from a clock that had moved on, and a case comparing a licence against itself would be
     * comparing two different ones.
     *
     * <p>The release ceiling sits above the release this deployment runs, so every case below
     * measures the compromise ceiling rather than the ordinary release entitlement.
     */
    private final LicensePayload held = withMaxRelease(
            withSeats(withFingerprints(LicenseTestFixture.defaults()), 10), "1.9");

    @Test
    @DisplayName("A token granting exactly what is held is accepted")
    void keepsWhatIsHeld() {
        Deployment deployment = deploymentHolding(held);

        deployment.register(LicenseTestFixture.tokenFor(held));

        assertThat(deployment.usable()).isTrue();
    }

    @Test
    @DisplayName("A token granting one seat more is refused")
    void refusesOneMoreSeat() {
        Deployment deployment = deploymentHolding(held);

        deployment.register(LicenseTestFixture.tokenFor(withSeats(held, 11)));

        assertThat(deployment.usable()).isFalse();
        assertThat(deployment.status()).isEqualTo(LicenseStatus.SIGNING_KEY_COMPROMISED);
        // The refusal is named apart from a broken signature, because the signature is sound and
        // a client that read it as one would send an operator looking for a damaged licence.
        assertThat(deployment.errorCode()).isEqualTo("LICENSE_SIGNING_KEY_COMPROMISED");
    }

    @Test
    @DisplayName("A token granting one feature more is refused")
    void refusesOneMoreFeature() {
        Deployment deployment = deploymentHolding(held);

        LicensePayload wider = withFeatures(held, List.of(
                LicenseTestFixture.FEATURE_GRANTED,
                LicenseTestFixture.FEATURE_SECOND_GRANTED,
                LicenseTestFixture.FEATURE_WITHHELD));
        deployment.register(LicenseTestFixture.tokenFor(wider));

        assertThat(deployment.usable()).isFalse();
        assertThat(deployment.status()).isEqualTo(LicenseStatus.SIGNING_KEY_COMPROMISED);
    }

    @Test
    @DisplayName("A token expiring one day later is refused")
    void refusesALaterExpiry() {
        Deployment deployment = deploymentHolding(held);

        LicensePayload later = withExpiry(held, held.expiresAt().plus(1, ChronoUnit.DAYS));
        deployment.register(LicenseTestFixture.tokenFor(later));

        assertThat(deployment.usable()).isFalse();
        assertThat(deployment.status()).isEqualTo(LicenseStatus.SIGNING_KEY_COMPROMISED);
    }

    @Test
    @DisplayName("A token that drops a capped quota is refused; an absent cap is unlimited")
    void refusesADroppedCap() {
        Deployment deployment = deploymentHolding(held);

        // Not a narrowing. A quota code the token does not name is uncapped, so dropping the one
        // that was capped asks for more than was held rather than for less.
        LicensePayload uncapped = withLimits(held, Map.of());
        deployment.register(LicenseTestFixture.tokenFor(uncapped));

        assertThat(deployment.usable()).isFalse();
        assertThat(deployment.status()).isEqualTo(LicenseStatus.SIGNING_KEY_COMPROMISED);
    }

    @Test
    @DisplayName("A token raising the release ceiling is refused, ranked as versions and not as text")
    void refusesAHigherReleaseCeiling() {
        Deployment deployment = deploymentHolding(held);

        // Ranked as text, 1.10 reads as lower than 1.9 and this token would pass. Release 1.10 is
        // an edition nobody bought, and the deployment refuses it on its own once it runs there.
        LicensePayload newer = withMaxRelease(held, "1.10");
        deployment.register(LicenseTestFixture.tokenFor(newer));

        assertThat(deployment.usable()).isFalse();
        assertThat(deployment.status()).isEqualTo(LicenseStatus.SIGNING_KEY_COMPROMISED);
    }

    @Test
    @DisplayName("A token naming the same release ceiling is accepted")
    void keepsTheSameReleaseCeiling() {
        Deployment deployment = deploymentHolding(held);

        deployment.register(LicenseTestFixture.tokenFor(withMaxRelease(held, "1.9")));

        assertThat(deployment.usable()).isTrue();
    }

    @Test
    @DisplayName("A token that drops the release ceiling is refused; an absent ceiling is unlimited")
    void refusesADroppedReleaseCeiling() {
        Deployment deployment = deploymentHolding(held);

        // Not a narrowing either. A token naming no ceiling entitles every release there will
        // ever be, so dropping the one that was pinned asks for more than was held.
        LicensePayload uncapped = withMaxRelease(held, null);
        deployment.register(LicenseTestFixture.tokenFor(uncapped));

        assertThat(deployment.usable()).isFalse();
        assertThat(deployment.status()).isEqualTo(LicenseStatus.SIGNING_KEY_COMPROMISED);
    }

    @Test
    @DisplayName("A deployment that held nothing is granted nothing")
    void refusesEverythingWhenNothingWasHeld() {
        Deployment deployment = deploymentHolding(null);

        deployment.register(LicenseTestFixture.tokenFor(held));

        assertThat(deployment.usable()).isFalse();
        assertThat(deployment.status()).isEqualTo(LicenseStatus.SIGNING_KEY_COMPROMISED);
    }

    @Test
    @DisplayName("A retired key still grants more seats; the ceiling is the compromised key's")
    void retiredKeyIsNotCapped() {
        Deployment deployment = deploymentHolding(held);

        LicensePayload wider = withKeyId(withSeats(held, 11), LicenseTestFixture.RETIRED_KEY_ID);
        deployment.register(LicenseTestFixture.tokenSignedByRetiredKey(wider));

        assertThat(deployment.usable()).isTrue();
    }

    /**
     * @param held what the deployment holds when it learns of the compromise, null when it holds
     *        nothing
     * @return a deployment that has already fixed its ceiling
     */
    private Deployment deploymentHolding(LicensePayload held) {
        Deployment deployment = new Deployment(workingDirectory);
        if (held != null) {
            deployment.store.save(RegistrationRecord.ofToken(LicenseTestFixture.tokenFor(held)));
        }
        deployment.manager.initialize();
        return deployment;
    }

    private static LicensePayload withFingerprints(LicensePayload payload) {
        return new LicensePayload(payload.schemaVersion(), payload.alg(), payload.kid(),
                payload.licenseId(), payload.activationId(), payload.customerId(),
                payload.customer(), payload.channel(), payload.productCode(), payload.maxRelease(),
                payload.tierLabel(), payload.issuedAt(), payload.activatedAt(),
                payload.expiresAt(), payload.gracePeriodDays(),
                LicenseTestFixture.machineFingerprints(), payload.nonce(), payload.features(),
                payload.limits(), payload.heartbeatRequired(), payload.heartbeatIntervalDays(),
                payload.heartbeatToleranceDays(), payload.metadata());
    }

    private static LicensePayload withSeats(LicensePayload payload, long seats) {
        return withLimits(payload, Map.of(SEATS, seats));
    }

    private static LicensePayload withLimits(LicensePayload payload, Map<String, Long> limits) {
        return new LicensePayload(payload.schemaVersion(), payload.alg(), payload.kid(),
                payload.licenseId(), payload.activationId(), payload.customerId(),
                payload.customer(), payload.channel(), payload.productCode(), payload.maxRelease(),
                payload.tierLabel(), payload.issuedAt(), payload.activatedAt(),
                payload.expiresAt(), payload.gracePeriodDays(), payload.machineFingerprints(),
                payload.nonce(), payload.features(), limits,
                payload.heartbeatRequired(), payload.heartbeatIntervalDays(),
                payload.heartbeatToleranceDays(), payload.metadata());
    }

    private static LicensePayload withFeatures(LicensePayload payload, List<String> features) {
        return new LicensePayload(payload.schemaVersion(), payload.alg(), payload.kid(),
                payload.licenseId(), payload.activationId(), payload.customerId(),
                payload.customer(), payload.channel(), payload.productCode(), payload.maxRelease(),
                payload.tierLabel(), payload.issuedAt(), payload.activatedAt(),
                payload.expiresAt(), payload.gracePeriodDays(), payload.machineFingerprints(),
                payload.nonce(), features, payload.limits(), payload.heartbeatRequired(),
                payload.heartbeatIntervalDays(), payload.heartbeatToleranceDays(),
                payload.metadata());
    }

    private static LicensePayload withExpiry(LicensePayload payload, Instant expiresAt) {
        return new LicensePayload(payload.schemaVersion(), payload.alg(), payload.kid(),
                payload.licenseId(), payload.activationId(), payload.customerId(),
                payload.customer(), payload.channel(), payload.productCode(), payload.maxRelease(),
                payload.tierLabel(), payload.issuedAt(), payload.activatedAt(), expiresAt,
                payload.gracePeriodDays(), payload.machineFingerprints(), payload.nonce(),
                payload.features(), payload.limits(), payload.heartbeatRequired(),
                payload.heartbeatIntervalDays(), payload.heartbeatToleranceDays(),
                payload.metadata());
    }

    private static LicensePayload withMaxRelease(LicensePayload payload, String maxRelease) {
        return new LicensePayload(payload.schemaVersion(), payload.alg(), payload.kid(),
                payload.licenseId(), payload.activationId(), payload.customerId(),
                payload.customer(), payload.channel(), payload.productCode(), maxRelease,
                payload.tierLabel(), payload.issuedAt(), payload.activatedAt(),
                payload.expiresAt(), payload.gracePeriodDays(), payload.machineFingerprints(),
                payload.nonce(), payload.features(), payload.limits(),
                payload.heartbeatRequired(), payload.heartbeatIntervalDays(),
                payload.heartbeatToleranceDays(), payload.metadata());
    }

    private static LicensePayload withKeyId(LicensePayload payload, String keyId) {
        return new LicensePayload(payload.schemaVersion(), payload.alg(), keyId,
                payload.licenseId(), payload.activationId(), payload.customerId(),
                payload.customer(), payload.channel(), payload.productCode(), payload.maxRelease(),
                payload.tierLabel(), payload.issuedAt(), payload.activatedAt(),
                payload.expiresAt(), payload.gracePeriodDays(), payload.machineFingerprints(),
                payload.nonce(), payload.features(), payload.limits(),
                payload.heartbeatRequired(), payload.heartbeatIntervalDays(),
                payload.heartbeatToleranceDays(), payload.metadata());
    }

    /**
     * A stand-in deployment carrying one compromised key and one retired key, assembled the way
     * the auto-configuration assembles the real one.
     */
    private static final class Deployment {

        private final MemoryStore store = new MemoryStore();
        private final LicenseState state;
        private final LicenseManager manager;

        private Deployment(Path workingDirectory) {
            VerificationKeys keys = new VerificationKeys(List.of(
                    new VerificationKey(LicenseTestFixture.KEY_ID,
                            LicenseTestFixture.publicKeyPem(), true),
                    new VerificationKey(LicenseTestFixture.RETIRED_KEY_ID,
                            LicenseTestFixture.retiredPublicKeyPem(), false)));

            LicenseGate gate = new LicenseGate();
            this.state = new LicenseState(gate);

            LicenseProperties properties = new LicenseProperties();
            properties.setTokenPath(workingDirectory.resolve("license.key").toString());
            properties.setStatePath(workingDirectory.resolve("license-state.json").toString());

            dev.accesscore.license.sdk.LicenseManager sdk =
                    dev.accesscore.license.sdk.LicenseManager
                            .builder(store, LicenseTestFixture.identity(), keys.materials())
                            .gate(gate)
                            .build();

            this.manager = new LicenseManager(properties, sdk, new RuntimeIntegrityChecker(null),
                    state, new LicenseAuditTrail(noRecorder()), keys, store, "fingerprint");
        }

        /**
         * @param token the token the deployment is handed next
         */
        private void register(String token) {
            manager.applyRecord(RegistrationRecord.ofToken(token));
        }

        private boolean usable() {
            return state.isUsable();
        }

        private LicenseStatus status() {
            return state.snapshot().evaluation().status();
        }

        private String errorCode() {
            return state.snapshot().evaluation().errorCode();
        }

        private static ObjectProvider<AuditRecorder> noRecorder() {
            return new ObjectProvider<>() {
                @Override
                public AuditRecorder getObject() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public AuditRecorder getObject(Object... args) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public AuditRecorder getIfAvailable() {
                    return null;
                }

                @Override
                public AuditRecorder getIfUnique() {
                    return null;
                }
            };
        }
    }

    /** A store holding the registration and the fixed ceiling in memory. */
    private static final class MemoryStore implements LicenseStore, CompromiseCeilingStore {

        private RegistrationRecord record;
        private CompromiseCeiling ceiling;

        @Override
        public Optional<RegistrationRecord> load() {
            return Optional.ofNullable(record);
        }

        @Override
        public void save(RegistrationRecord value) {
            this.record = value;
        }

        @Override
        public void clear() {
            this.record = null;
        }

        @Override
        public Optional<CompromiseCeiling> loadCeiling() {
            return Optional.ofNullable(ceiling);
        }

        @Override
        public void saveCeiling(CompromiseCeiling value) {
            this.ceiling = value;
        }
    }
}
