package dev.simplecore.simplix.license.activation;

import dev.simplecore.simplix.license.LicenseTestFixture;
import dev.simplecore.simplix.license.config.VerificationKey;
import dev.simplecore.simplix.license.config.VerificationKeys;
import dev.accesscore.license.sdk.LicenseManager;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import dev.accesscore.license.sdk.protocol.ActivationModel.PreparedRequest;
import dev.accesscore.license.sdk.spi.LicenseSpi.LicenseStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a deployment tells the issuing server about the keys it can verify under.
 *
 * <p>Every name the build carries travels on the activation and on every heartbeat, so the server
 * signs under one of them. Without it a server that has moved to a newer key signs the next
 * refreshed token under a name this deployment has never heard of, and a licence that was working
 * a minute ago stops verifying — with nothing wrong at either end.
 */
@DisplayName("Activation requests - the verification keys this deployment carries")
class ReportedVerificationKeysTest {

    private final VerificationKeys carried = new VerificationKeys(List.of(
            new VerificationKey(LicenseTestFixture.KEY_ID, LicenseTestFixture.publicKeyPem(),
                    false),
            new VerificationKey(LicenseTestFixture.RETIRED_KEY_ID,
                    LicenseTestFixture.retiredPublicKeyPem(), false)));

    @Test
    @DisplayName("An activation names every key this build carries")
    void activationNamesEveryCarriedKey() {
        PreparedRequest request = manager()
                .activationRequest("TEST-00000-00000-00000-00000-00000", Instant.now());

        assertThat(request.body()).contains("\"knownVerificationKeys\":[\""
                + LicenseTestFixture.KEY_ID + "\",\"" + LicenseTestFixture.RETIRED_KEY_ID + "\"]");
    }

    @Test
    @DisplayName("A heartbeat names them again, so a key that arrived in a release is picked up")
    void heartbeatNamesThemAgain() {
        PreparedRequest request = manager().heartbeatRequest(Instant.now());

        assertThat(request.body()).contains("\"knownVerificationKeys\":[\""
                + LicenseTestFixture.KEY_ID + "\",\"" + LicenseTestFixture.RETIRED_KEY_ID + "\"]");
    }

    /**
     * @return a manager assembled over the carried keys the way the auto-configuration does
     */
    private LicenseManager manager() {
        return LicenseManager
                .builder(new RegisteredStore(), LicenseTestFixture.identity(),
                        carried.materials())
                .build();
    }

    /** A store holding a registered seat, which is what a heartbeat is written from. */
    private static final class RegisteredStore implements LicenseStore {

        private static final RegistrationRecord REGISTERED = new RegistrationRecord(
                null, LicenseTestFixture.defaultToken(), "act_1", "secret", "inst_1", null, null,
                null);

        @Override
        public Optional<RegistrationRecord> load() {
            return Optional.of(REGISTERED);
        }

        @Override
        public void save(RegistrationRecord record) {
        }

        @Override
        public void clear() {
        }
    }
}
