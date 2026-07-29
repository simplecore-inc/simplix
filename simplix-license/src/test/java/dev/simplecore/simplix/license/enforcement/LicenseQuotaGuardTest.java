package dev.simplecore.simplix.license.enforcement;

import dev.simplecore.simplix.license.RecordingLicenseAuditRecorder;
import dev.simplecore.simplix.license.core.LicenseAuditTrail;
import dev.accesscore.license.sdk.gate.LicenseGate;
import dev.accesscore.license.sdk.model.LicenseModel.EvaluationResponse;
import dev.accesscore.license.sdk.model.LicenseStatus;
import dev.accesscore.license.sdk.spi.LicenseSpi.QuotaCounter;
import dev.simplecore.simplix.core.exception.SimpliXGeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the guard refuses, and what it deliberately does not.
 *
 * <p>The ceiling itself is the core's; what is asserted here is this product's rule about it —
 * only new registrations are refused, and equipment already installed keeps working.
 */
class LicenseQuotaGuardTest {

    private static final String QUOTA = "QUOTA_COUNTED";

    private final RecordingLicenseAuditRecorder recorder = new RecordingLicenseAuditRecorder();

    /**
     * @param limits the ceilings the license carries
     * @return a gate primed with a usable license carrying them
     */
    private static LicenseGate gateWith(Map<String, Long> limits) {
        LicenseGate gate = new LicenseGate();
        gate.refresh(new EvaluationResponse(LicenseStatus.VALID, true,
                LicenseStatus.VALID.errorCode(), null, null, List.of(), limits, null, null,
                null));
        return gate;
    }

    /**
     * @param count what the counter answers
     * @return a counter for the one quota these cases use
     */
    private static QuotaCounter counterAt(long count) {
        return new QuotaCounter() {
            @Override
            public List<String> countedQuotaCodes() {
                return List.of(QUOTA);
            }

            @Override
            public long count(String quotaCode) {
                return count;
            }
        };
    }

    /**
     * @param gate the gate holding the ceilings
     * @param counter the counter answering for the quota
     * @return the guard under test
     */
    private LicenseQuotaGuard guard(LicenseGate gate, QuotaCounter counter) {
        return new LicenseQuotaGuard(gate, List.of(counter),
                new LicenseAuditTrail(new ObjectProvider<>() {
                    @Override
                    public dev.accesscore.license.sdk.spi.LicenseSpi.AuditRecorder getObject() {
                        return recorder;
                    }

                    @Override
                    public dev.accesscore.license.sdk.spi.LicenseSpi.AuditRecorder getIfAvailable() {
                        return recorder;
                    }
                }));
    }

    @Test
    @DisplayName("One more below the ceiling is allowed")
    void belowTheCeilingIsAllowed() {
        assertThatCode(() -> guard(gateWith(Map.of(QUOTA, 5L)), counterAt(4)).require(QUOTA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("One more at the ceiling is refused, and the refusal is recorded")
    void atTheCeilingIsRefused() {
        assertThatThrownBy(() -> guard(gateWith(Map.of(QUOTA, 5L)), counterAt(5)).require(QUOTA))
                .isInstanceOf(SimpliXGeneralException.class)
                .hasMessageContaining("error.license.quotaReached");
        assertThat(recorder.limitsReached).containsExactly(QUOTA + ":5/5");
    }

    @Test
    @DisplayName("A batch that would pass the ceiling is refused whole")
    void aBatchThatWouldNotFitIsRefused() {
        LicenseQuotaGuard guard = guard(gateWith(Map.of(QUOTA, 10L)), counterAt(4));
        assertThatCode(() -> guard.require(QUOTA, 6)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.require(QUOTA, 7))
                .isInstanceOf(SimpliXGeneralException.class);
    }

    @Test
    @DisplayName("Registering nothing is allowed even past the ceiling")
    void registeringNothingIsAllowed() {
        // A caller that reconciles a collection down to zero new rows is not a new
        // registration, and a deployment already past its ceiling has to keep operating.
        assertThatCode(() -> guard(gateWith(Map.of(QUOTA, 1L)), counterAt(9)).require(QUOTA, 0))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A quota the license does not name is unlimited")
    void anUnnamedQuotaIsUnlimited() {
        assertThatCode(() -> guard(gateWith(Map.of()), counterAt(1_000_000)).require(QUOTA))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A ceiling of zero refuses the first one")
    void aCeilingOfZeroRefusesTheFirstOne() {
        // Zero is zero, not "unset". A contract that sold none of something sold none.
        assertThatThrownBy(() -> guard(gateWith(Map.of(QUOTA, 0L)), counterAt(0)).require(QUOTA))
                .isInstanceOf(SimpliXGeneralException.class);
    }

    @Test
    @DisplayName("A quota nothing counts is not enforced and not reported")
    void anUncountedQuotaIsNotEnforced() {
        LicenseQuotaGuard guard = guard(gateWith(Map.of("OTHER", 1L)), counterAt(3));
        assertThatCode(() -> guard.require("OTHER")).doesNotThrowAnyException();
        assertThat(guard.currentUsage()).containsOnlyKeys(QUOTA);
    }

    @Test
    @DisplayName("What this deployment can count is what it reports")
    void whatIsCountedIsWhatIsReported() {
        LicenseQuotaGuard guard = guard(gateWith(Map.of()), counterAt(3));
        assertThat(guard.countedQuotaCodes()).containsExactly(QUOTA);
        assertThat(guard.currentCount(QUOTA)).isEqualTo(3);
        assertThat(guard.currentUsage()).containsEntry(QUOTA, 3L);
    }
}
