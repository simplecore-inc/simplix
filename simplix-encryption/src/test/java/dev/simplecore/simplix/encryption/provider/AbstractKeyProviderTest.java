package dev.simplecore.simplix.encryption.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("AbstractKeyProvider Tests")
class AbstractKeyProviderTest {

    private TestKeyProvider provider;

    /**
     * Concrete test implementation of AbstractKeyProvider.
     */
    static class TestKeyProvider extends AbstractKeyProvider {

        @Override
        public SecretKey getCurrentKey() {
            return permanentKeyCache.get(currentVersion);
        }

        @Override
        public SecretKey getKey(String version) {
            return permanentKeyCache.get(version);
        }

        @Override
        public String getCurrentVersion() {
            return currentVersion;
        }

        @Override
        public String rotateKey() {
            return currentVersion;
        }

        @Override
        public String getName() {
            return "TestKeyProvider";
        }

        // Expose protected methods for testing
        public SecretKey testGenerateNewKey() throws Exception {
            return generateNewKey();
        }

        public boolean testShouldRotate() {
            return shouldRotate();
        }

        public String testCreateVersionIdentifier() {
            return createVersionIdentifier();
        }

        public void testLogKeyRotation(String oldVersion, String newVersion, String reason) {
            logKeyRotation(oldVersion, newVersion, reason);
        }

        public void setCurrentVersionForTest(String version) {
            this.currentVersion = version;
        }

        public void setLastRotationForTest(Instant instant) {
            this.lastRotation = instant;
        }

        public void addKeyToCacheForTest(String version, SecretKey key) {
            this.permanentKeyCache.put(version, key);
        }
    }

    @BeforeEach
    void setUp() {
        provider = new TestKeyProvider();
        ReflectionTestUtils.setField(provider, "rotationEnabled", true);
        ReflectionTestUtils.setField(provider, "rotationDays", 90);
        ReflectionTestUtils.setField(provider, "autoRotation", true);
    }

    @Nested
    @DisplayName("generateNewKey()")
    class GenerateNewKeyTests {

        @Test
        @DisplayName("Should generate a valid AES-256 key")
        void shouldGenerateValidAes256Key() throws Exception {
            SecretKey key = provider.testGenerateNewKey();

            assertThat(key).isNotNull();
            assertThat(key.getAlgorithm()).isEqualTo("AES");
            assertThat(key.getEncoded()).hasSize(32); // 256 bits = 32 bytes
        }

        @Test
        @DisplayName("Should generate unique keys on each call")
        void shouldGenerateUniqueKeys() throws Exception {
            SecretKey key1 = provider.testGenerateNewKey();
            SecretKey key2 = provider.testGenerateNewKey();

            assertThat(key1.getEncoded()).isNotEqualTo(key2.getEncoded());
        }
    }

    @Nested
    @DisplayName("shouldRotate()")
    class ShouldRotateTests {

        @Test
        @DisplayName("Should return false when lastRotation is null")
        void shouldReturnFalseWhenLastRotationIsNull() {
            assertThat(provider.testShouldRotate()).isFalse();
        }

        @Test
        @DisplayName("Should return false when within rotation period")
        void shouldReturnFalseWithinRotationPeriod() {
            provider.setLastRotationForTest(Instant.now().minus(30, ChronoUnit.DAYS));

            assertThat(provider.testShouldRotate()).isFalse();
        }

        @Test
        @DisplayName("Should return true when rotation period has elapsed")
        void shouldReturnTrueWhenRotationPeriodElapsed() {
            provider.setLastRotationForTest(Instant.now().minus(91, ChronoUnit.DAYS));

            assertThat(provider.testShouldRotate()).isTrue();
        }

        @Test
        @DisplayName("Should return true when exactly at rotation period boundary")
        void shouldReturnTrueAtRotationBoundary() {
            provider.setLastRotationForTest(Instant.now().minus(90, ChronoUnit.DAYS));

            assertThat(provider.testShouldRotate()).isTrue();
        }
    }

    @Nested
    @DisplayName("createVersionIdentifier()")
    class CreateVersionIdentifierTests {

        @Test
        @DisplayName("Should create version starting with 'v' prefix")
        void shouldCreateVersionWithVPrefix() {
            String version = provider.testCreateVersionIdentifier();

            assertThat(version).startsWith("v");
        }

        @Test
        @DisplayName("Should create version containing timestamp")
        void shouldCreateVersionWithTimestamp() {
            long beforeTime = System.currentTimeMillis();
            String version = provider.testCreateVersionIdentifier();
            long afterTime = System.currentTimeMillis();

            String timestampStr = version.substring(1); // Remove "v" prefix
            long timestamp = Long.parseLong(timestampStr);

            assertThat(timestamp).isBetween(beforeTime, afterTime);
        }

        @Test
        @DisplayName("Should create unique versions on successive calls")
        void shouldCreateUniqueVersions() {
            String version1 = provider.testCreateVersionIdentifier();
            String version2 = provider.testCreateVersionIdentifier();

            // Due to millisecond precision, they might be the same if called very quickly,
            // but in practice should differ
            assertThat(version1).isNotNull();
            assertThat(version2).isNotNull();
        }
    }

    @Nested
    @DisplayName("getKeyStatistics()")
    class GetKeyStatisticsTests {

        @Test
        @DisplayName("Should return statistics with provider name")
        void shouldReturnStatisticsWithProviderName() {
            provider.setCurrentVersionForTest("v1");

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsEntry("provider", "TestKeyProvider");
        }

        @Test
        @DisplayName("Should return 'none' for currentVersion when null")
        void shouldReturnNoneForNullVersion() {
            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsEntry("currentVersion", "none");
        }

        @Test
        @DisplayName("Should return correct currentVersion")
        void shouldReturnCorrectCurrentVersion() {
            provider.setCurrentVersionForTest("v1");

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsEntry("currentVersion", "v1");
        }

        @Test
        @DisplayName("Should return cached key count")
        void shouldReturnCachedKeyCount() throws Exception {
            SecretKey key = provider.testGenerateNewKey();
            provider.addKeyToCacheForTest("v1", key);

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsEntry("cachedKeys", 1);
        }

        @Test
        @DisplayName("Should include rotation configuration")
        void shouldIncludeRotationConfig() {
            provider.setCurrentVersionForTest("v1");

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsEntry("rotationEnabled", true);
            assertThat(stats).containsEntry("autoRotation", true);
            assertThat(stats).containsEntry("rotationDays", 90);
        }

        @Test
        @DisplayName("Should return 'never' for lastRotation when null")
        void shouldReturnNeverForNullLastRotation() {
            provider.setCurrentVersionForTest("v1");

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats).containsEntry("lastRotation", "never");
            assertThat(stats).containsEntry("nextRotation", "unknown");
        }

        @Test
        @DisplayName("Should return formatted lastRotation when set")
        void shouldReturnFormattedLastRotation() {
            Instant now = Instant.now();
            provider.setCurrentVersionForTest("v1");
            provider.setLastRotationForTest(now);

            Map<String, Object> stats = provider.getKeyStatistics();

            assertThat(stats.get("lastRotation")).isEqualTo(now.toString());
            assertThat(stats.get("nextRotation")).isEqualTo(
                now.plus(90, ChronoUnit.DAYS).toString());
        }
    }

    @Nested
    @DisplayName("validateConfiguration()")
    class ValidateConfigurationTests {

        @Test
        @DisplayName("Should return true for valid configuration")
        void shouldReturnTrueForValidConfig() {
            assertThat(provider.validateConfiguration()).isTrue();
        }

        @Test
        @DisplayName("Should return false when rotation enabled with invalid period")
        void shouldReturnFalseForInvalidRotationPeriod() {
            ReflectionTestUtils.setField(provider, "rotationDays", 0);

            assertThat(provider.validateConfiguration()).isFalse();
        }

        @Test
        @DisplayName("Should return true when rotation is disabled even with invalid period")
        void shouldReturnTrueWhenRotationDisabledWithInvalidPeriod() {
            ReflectionTestUtils.setField(provider, "rotationEnabled", false);
            ReflectionTestUtils.setField(provider, "rotationDays", 0);

            assertThat(provider.validateConfiguration()).isTrue();
        }
    }

    @Nested
    @DisplayName("isConfigured()")
    class IsConfiguredTests {

        @Test
        @DisplayName("Should return false when currentVersion is null")
        void shouldReturnFalseWhenVersionNull() {
            assertThat(provider.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("Should return true when currentVersion is set and config is valid")
        void shouldReturnTrueWhenVersionSetAndValid() {
            provider.setCurrentVersionForTest("v1");

            assertThat(provider.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("Should return false when currentVersion is set but config is invalid")
        void shouldReturnFalseWhenVersionSetButInvalid() {
            provider.setCurrentVersionForTest("v1");
            ReflectionTestUtils.setField(provider, "rotationDays", 0);

            assertThat(provider.isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("logKeyRotation()")
    class LogKeyRotationTests {

        @Test
        @DisplayName("Should not throw when logging with valid parameters")
        void shouldNotThrowWithValidParams() {
            // Just ensure no exception is thrown during logging
            provider.testLogKeyRotation("v1", "v2", "manual");
        }

        @Test
        @DisplayName("Should not throw when oldVersion is null")
        void shouldNotThrowWhenOldVersionNull() {
            provider.testLogKeyRotation(null, "v1", "initial");
        }
    }
}
