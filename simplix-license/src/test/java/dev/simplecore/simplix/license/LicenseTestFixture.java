package dev.simplecore.simplix.license;

import dev.accesscore.license.sdk.ffi.AclicCore;
import dev.accesscore.license.sdk.issuing.LicenseIssuer;
import dev.accesscore.license.sdk.model.LicenseChannel;
import dev.accesscore.license.sdk.model.LicenseModel.CollectedIdentifiers;
import dev.accesscore.license.sdk.model.LicenseModel.EvaluationRequest;
import dev.accesscore.license.sdk.model.LicenseModel.EvaluationResponse;
import dev.accesscore.license.sdk.model.LicenseModel.KeyPairDescription;
import dev.accesscore.license.sdk.model.LicenseModel.KeyMaterial;
import dev.accesscore.license.sdk.model.LicenseModel.LicensePayload;
import dev.accesscore.license.sdk.model.SignatureAlgorithm;
import dev.accesscore.license.sdk.spi.LicenseSpi.ProductIdentity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Builds signed licenses for the tests, using the SDK that issues and judges them in
 * production.
 *
 * <p>A throwaway key pair is generated once per JVM, so the tests exercise the same signing and
 * verification path a customer's deployment does without the production key taking part.
 *
 * <p>Nothing here writes a payload by hand. A fixture that assembled its own JSON would be a
 * second definition of the format, and a test that passed against it would prove only that the
 * fixture and the test agree.
 */
public final class LicenseTestFixture {

    /** The one machine identifier the stand-in deployment reports. */
    public static final String FINGERPRINT = "sha256:testmachine";

    // Deliberately not a real product's keys. Licensing gates on whatever string a payload
    // carries and must never be written against one product's vocabulary; a test that used the
    // shipped keys would stop proving that.
    public static final String FEATURE_GRANTED = "FEATURE_GRANTED";
    public static final String FEATURE_SECOND_GRANTED = "FEATURE_SECOND_GRANTED";
    public static final String FEATURE_WITHHELD = "FEATURE_WITHHELD";
    public static final String QUOTA_COUNTED = "QUOTA_COUNTED";

    /** Product this fixture's payloads are issued for. */
    public static final String PRODUCT_CODE = "TEST_PRODUCT";

    /** Release the stand-in deployment reports. */
    public static final String RELEASE = "1.0.0";

    /** The name the fixture's verification key is filed under. */
    public static final String KEY_ID = "test-2026-01";

    /** The name a key that no longer signs but is still carried is filed under. */
    public static final String RETIRED_KEY_ID = "test-2024-01";

    private static final LicenseIssuer ISSUER = new LicenseIssuer();
    private static final AclicCore.GeneratedPair<KeyPairDescription> KEY_PAIR =
            ISSUER.generateKeyPair(SignatureAlgorithm.ED25519);
    private static final AclicCore.GeneratedPair<KeyPairDescription> RETIRED_KEY_PAIR =
            ISSUER.generateKeyPair(SignatureAlgorithm.ED25519);
    private static final AclicCore.GeneratedPair<KeyPairDescription> FOREIGN_KEY_PAIR =
            ISSUER.generateKeyPair(SignatureAlgorithm.ED25519);

    private LicenseTestFixture() {
    }

    /**
     * @return the key a deployment carries to accept this fixture's licenses
     */
    public static List<KeyMaterial> keys() {
        return List.of(KeyMaterial.of(KEY_ID, KEY_PAIR.described().publicKeyPem()));
    }

    /**
     * @return a key that signs nothing this fixture issues, for signature-rejection cases
     */
    public static List<KeyMaterial> foreignKeys() {
        return List.of(KeyMaterial.of(KEY_ID, FOREIGN_KEY_PAIR.described().publicKeyPem()));
    }

    /**
     * @return the fixture's verification key in PEM form
     */
    public static String publicKeyPem() {
        return KEY_PAIR.described().publicKeyPem();
    }

    /**
     * @return the verification half of the key a deployment still carries but no longer receives
     *         fresh licenses under
     */
    public static String retiredPublicKeyPem() {
        return RETIRED_KEY_PAIR.described().publicKeyPem();
    }

    /**
     * @param payload the payload to sign, which must name {@link #RETIRED_KEY_ID}
     * @return a token signed by the retired key
     */
    public static String tokenSignedByRetiredKey(LicensePayload payload) {
        return ISSUER.sign(payload, RETIRED_KEY_PAIR.privateKey()).token();
    }

    /**
     * What the machine running these tests reports about itself.
     *
     * <p>A case that drives a whole deployment rather than one judgement cannot say which
     * machine it is, because the core asks the host it runs on. A payload stamped for these is
     * one this machine accepts.
     *
     * @return this machine's identifiers
     */
    public static List<String> machineFingerprints() {
        return AclicCore.shared().fingerprint(CollectedIdentifiers.class).fingerprintsOrEmpty();
    }

    /**
     * A production-channel payload valid for a year, bound to {@link #FINGERPRINT}.
     *
     * @return the payload, which a caller adjusts per test with the record's wither
     */
    public static LicensePayload defaults() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new LicensePayload(
                1,
                SignatureAlgorithm.ED25519.wireName(),
                KEY_ID,
                "lic_test_001",
                "act_test_001",
                "cus_test_001",
                "Test Corp",
                LicenseChannel.PRODUCTION,
                PRODUCT_CODE,
                null,
                "Enterprise",
                now.minus(1, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                now.plus(365, ChronoUnit.DAYS),
                30,
                List.of(FINGERPRINT),
                "test-nonce",
                List.of(FEATURE_GRANTED, FEATURE_SECOND_GRANTED),
                Map.of(),
                false,
                7,
                21,
                Map.of("issuer", "AccessCORE"));
    }

    /**
     * @param payload the payload to sign
     * @return the token a deployment would store
     */
    public static String tokenFor(LicensePayload payload) {
        return ISSUER.sign(payload, KEY_PAIR.privateKey()).token();
    }

    /**
     * @return a token for the default payload
     */
    public static String defaultToken() {
        return tokenFor(defaults());
    }

    /**
     * @param payload the payload to sign
     * @return a token signed by a key no deployment here carries
     */
    public static String tokenSignedByAnotherKey(LicensePayload payload) {
        return ISSUER.sign(payload, FOREIGN_KEY_PAIR.privateKey()).token();
    }

    /**
     * Judges a token the way this deployment would.
     *
     * @param token the token to judge
     * @param now the instant to judge it at
     * @return what the core concluded
     */
    public static EvaluationResponse judge(String token, Instant now) {
        return AclicCore.shared().evaluate(
                new EvaluationRequest(token, keys(), null, null, now, true, PRODUCT_CODE,
                        RELEASE, List.of(FINGERPRINT)),
                EvaluationResponse.class);
    }

    /**
     * @return the identity a stand-in deployment reports
     */
    public static ProductIdentity identity() {
        return new ProductIdentity() {
            @Override
            public String productCode() {
                return PRODUCT_CODE;
            }

            @Override
            public String release() {
                return RELEASE;
            }
        };
    }
}
