package dev.simplecore.simplix.license.core;

import dev.accesscore.license.sdk.LicenseKeys;
import dev.simplecore.simplix.license.LicenseTestFixture;
import dev.simplecore.simplix.license.config.LicenseProperties;
import dev.simplecore.simplix.license.config.VerificationKey;
import dev.simplecore.simplix.license.config.VerificationKeys;
import dev.simplecore.simplix.license.integrity.RuntimeIntegrityChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which keys a deployment will accept an issuing server's signature from.
 *
 * <p>A deployment carries more than one verification key — a retired one so the licences it
 * signed keep verifying, and a delegated one whenever an issuer certificate vouches for it. The
 * setup wizard decides whether an address is usable by comparing that server's signing key
 * against what this deployment trusts, and comparing against the FIRST key alone refuses an
 * issuing server that signs with a delegated key: exactly the arrangement a certificate exists
 * to permit, and one the activation path a step later accepts. The wizard was then the only
 * place that said no, and it said it in words that read as a wrong address.
 */
@DisplayName("Verification key fingerprints - what an address is checked against")
class VerificationKeyFingerprintsTest {

    @Test
    @DisplayName("should answer a fingerprint for every key carried, not only the first")
    void answersEveryCarriedKey() {
        LicenseManager manager = managerCarrying(
                new VerificationKey(LicenseTestFixture.KEY_ID, LicenseTestFixture.publicKeyPem(), false),
                new VerificationKey(LicenseTestFixture.RETIRED_KEY_ID,
                        LicenseTestFixture.retiredPublicKeyPem(), false));

        LicenseKeys keys = new LicenseKeys();
        String first = keys.fingerprintOf(LicenseTestFixture.publicKeyPem());
        String second = keys.fingerprintOf(LicenseTestFixture.retiredPublicKeyPem());

        assertThat(manager.verificationKeyFingerprints())
                .as("an issuing server signing with the second key is one this deployment can "
                        + "verify, so the wizard has to recognise it as usable")
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("should keep the single-key fingerprint as the one the startup line reports")
    void keepsTheReportedFingerprintSingular() {
        LicenseManager manager = managerCarrying(
                new VerificationKey(LicenseTestFixture.KEY_ID, LicenseTestFixture.publicKeyPem(), false),
                new VerificationKey(LicenseTestFixture.RETIRED_KEY_ID,
                        LicenseTestFixture.retiredPublicKeyPem(), false));

        assertThat(manager.getPublicKeyFingerprint())
                .as("the startup line and the status screen print one value; widening that would "
                        + "change what an operator compares against the issuer's own log")
                .isEqualTo("reported-fingerprint");
    }

    /**
     * A manager built around the carried keys and nothing else.
     *
     * <p>Both questions here read a field — the key set and the reported fingerprint — so the
     * collaborators that judge, store and record are left null rather than stood up. A null that
     * were reached would fail loudly at that line, which is the outcome worth having: it would
     * mean the answer came from somewhere other than the keys.
     *
     * @param carried the keys the deployment is built with
     * @return the manager
     */
    private static LicenseManager managerCarrying(VerificationKey... carried) {
        VerificationKeys keys = new VerificationKeys(List.of(carried));
        return new LicenseManager(new LicenseProperties(), null, new RuntimeIntegrityChecker(null),
                null, null, keys, null, "reported-fingerprint");
    }
}
