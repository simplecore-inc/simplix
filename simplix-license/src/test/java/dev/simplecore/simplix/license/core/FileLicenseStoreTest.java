package dev.simplecore.simplix.license.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.accesscore.license.sdk.model.LicenseModel.RegistrationRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the file-backed store reports, now that the same state file also keeps the fixed ceiling.
 */
@DisplayName("FileLicenseStore - the registration and the ceiling in one state file")
class FileLicenseStoreTest {

    @TempDir
    private Path workingDirectory;

    private FileLicenseStore store;

    @BeforeEach
    void setUp() {
        store = new FileLicenseStore(
                workingDirectory.resolve("license.key"),
                workingDirectory.resolve("license-state.json"),
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    @DisplayName("A state file holding only the fixed ceiling is not a registration")
    void ceilingOnlyStateIsNotARegistration() {
        store.saveCeiling(CompromiseCeiling.nothing(Instant.parse("2026-08-01T00:00:00Z")));

        // Nothing is registered here — the file exists only to keep the ceiling. Reported as a
        // registration it would have every caller that asks believe a seat is held, down to
        // offering to release one this deployment never took.
        assertThat(store.load()).isEmpty();
        assertThat(store.loadCeiling()).isPresent();
    }

    @Test
    @DisplayName("Registering after the ceiling was fixed keeps both")
    void registrationDoesNotDropTheCeiling() {
        CompromiseCeiling fixed = CompromiseCeiling.nothing(Instant.parse("2026-08-01T00:00:00Z"));
        store.saveCeiling(fixed);

        store.save(new RegistrationRecord("KEY", "token", "act_1", "secret", "inst_1", null, null,
                null));

        assertThat(store.load()).isPresent();
        assertThat(store.loadCeiling()).contains(fixed);
    }
}
