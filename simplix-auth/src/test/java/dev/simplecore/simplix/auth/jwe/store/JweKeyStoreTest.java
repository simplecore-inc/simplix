package dev.simplecore.simplix.auth.jwe.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JweKeyStore default methods")
class JweKeyStoreTest {

    /**
     * Minimal implementation for testing default methods.
     */
    private static class TestJweKeyStore implements JweKeyStore {
        @Override
        public JweKeyData save(JweKeyData keyData) {
            return keyData;
        }

        @Override
        public java.util.Optional<JweKeyData> findByVersion(String version) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<JweKeyData> findCurrent() {
            return java.util.Optional.empty();
        }

        @Override
        public List<JweKeyData> findAll() {
            return List.of();
        }

        @Override
        public void deactivateAllExcept(String exceptVersion) {
        }
    }

    @Test
    @DisplayName("findExpired should return empty list by default")
    void findExpiredShouldReturnEmptyList() {
        JweKeyStore store = new TestJweKeyStore();
        assertThat(store.findExpired()).isEmpty();
    }

    @Test
    @DisplayName("deleteByVersion should return false by default")
    void deleteByVersionShouldReturnFalse() {
        JweKeyStore store = new TestJweKeyStore();
        assertThat(store.deleteByVersion("v1")).isFalse();
    }

    @Test
    @DisplayName("deleteExpired should return 0 when no expired keys")
    void deleteExpiredShouldReturnZero() {
        JweKeyStore store = new TestJweKeyStore();
        assertThat(store.deleteExpired()).isZero();
    }

    @Test
    @DisplayName("deleteExpired should iterate and delete expired keys")
    void deleteExpiredShouldIterateAndDelete() {
        JweKeyStore store = new JweKeyStore() {
            @Override
            public JweKeyData save(JweKeyData keyData) { return keyData; }
            @Override
            public java.util.Optional<JweKeyData> findByVersion(String version) { return java.util.Optional.empty(); }
            @Override
            public java.util.Optional<JweKeyData> findCurrent() { return java.util.Optional.empty(); }
            @Override
            public List<JweKeyData> findAll() { return List.of(); }
            @Override
            public void deactivateAllExcept(String exceptVersion) {}

            @Override
            public List<JweKeyData> findExpired() {
                return List.of(
                        JweKeyData.builder().version("v1").build(),
                        JweKeyData.builder().version("v2").build()
                );
            }

            @Override
            public boolean deleteByVersion(String version) {
                return "v1".equals(version); // Only v1 is successfully deleted
            }
        };

        int deleted = store.deleteExpired();
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    @DisplayName("deleteExpired should count all successful deletions")
    void deleteExpiredShouldCountAllSuccessful() {
        JweKeyStore store = new JweKeyStore() {
            @Override
            public JweKeyData save(JweKeyData keyData) { return keyData; }
            @Override
            public java.util.Optional<JweKeyData> findByVersion(String version) { return java.util.Optional.empty(); }
            @Override
            public java.util.Optional<JweKeyData> findCurrent() { return java.util.Optional.empty(); }
            @Override
            public List<JweKeyData> findAll() { return List.of(); }
            @Override
            public void deactivateAllExcept(String exceptVersion) {}

            @Override
            public List<JweKeyData> findExpired() {
                return List.of(
                        JweKeyData.builder().version("v1").build(),
                        JweKeyData.builder().version("v2").build(),
                        JweKeyData.builder().version("v3").build()
                );
            }

            @Override
            public boolean deleteByVersion(String version) {
                return true; // All deleted
            }
        };

        int deleted = store.deleteExpired();
        assertThat(deleted).isEqualTo(3);
    }
}
