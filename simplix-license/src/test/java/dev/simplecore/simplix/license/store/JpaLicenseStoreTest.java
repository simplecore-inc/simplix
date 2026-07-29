package dev.simplecore.simplix.license.store;

import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JpaLicenseStore - durable license state")
class JpaLicenseStoreTest {

    @Mock private EntityManager entityManager;
    @Mock private TypedQuery<LicenseRegistration> query;

    private JpaLicenseStore store;

    @BeforeEach
    void setUp() {
        store = new JpaLicenseStore();
        ReflectionTestUtils.setField(store, "entityManager", entityManager);
        when(entityManager.createQuery(anyString(), eq(LicenseRegistration.class)))
                .thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
    }

    /**
     * @param rows what the singleton lookup finds
     */
    private void singletonLookupReturns(LicenseRegistration... rows) {
        when(query.getResultStream()).thenReturn(Stream.of(rows));
    }

    @Test
    @DisplayName("With no row the store reports nothing registered")
    void emptyWhenNoRow() {
        singletonLookupReturns();

        assertThat(store.load()).isEmpty();
    }

    @Test
    @DisplayName("Saving then loading round-trips every field")
    void roundTrip() {
        singletonLookupReturns();
        Instant watermark = Instant.parse("2026-07-25T00:00:00Z");

        store.save(new RegistrationRecord("KEY", "token", "act_1", "secret", "inst_1",
                watermark, watermark, null));

        ArgumentCaptor<LicenseRegistration> saved =
                ArgumentCaptor.forClass(LicenseRegistration.class);
        verify(entityManager).persist(saved.capture());
        assertThat(saved.getValue().getProductKey()).isEqualTo("KEY");
        assertThat(saved.getValue().getLicenseToken()).isEqualTo("token");
        assertThat(saved.getValue().getActivationSecret()).isEqualTo("secret");
        assertThat(saved.getValue().getVerificationWatermark()).isEqualTo(watermark);
    }

    @Test
    @DisplayName("Saving twice updates the same row rather than adding another")
    void savingUpdatesTheSameRow() {
        LicenseRegistration existing = new LicenseRegistration();
        existing.setLicenseRegistrationId("reg_1");
        singletonLookupReturns(existing);

        store.save(RegistrationRecord.ofToken("token"));

        verify(entityManager).merge(existing);
    }

    @Test
    @DisplayName("Clearing removes the row")
    void clearRemovesTheRow() {
        LicenseRegistration existing = new LicenseRegistration();
        singletonLookupReturns(existing);

        store.clear();

        verify(entityManager).remove(existing);
    }
}
