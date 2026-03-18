package dev.simplecore.simplix.scheduler.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchedulerProperties - Configuration properties and exclusion logic")
class SchedulerPropertiesTest {

    private SchedulerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SchedulerProperties();
    }

    @Nested
    @DisplayName("Default values")
    class DefaultValuesTest {

        @Test
        @DisplayName("Should have enabled=true by default")
        void shouldBeEnabledByDefault() {
            assertThat(properties.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should have aspectEnabled=true by default")
        void shouldHaveAspectEnabledByDefault() {
            assertThat(properties.isAspectEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should have mode='database' by default")
        void shouldHaveDatabaseModeByDefault() {
            assertThat(properties.getMode()).isEqualTo("database");
        }

        @Test
        @DisplayName("Should have retentionDays=90 by default")
        void shouldHaveRetentionDays90ByDefault() {
            assertThat(properties.getRetentionDays()).isEqualTo(90);
        }

        @Test
        @DisplayName("Should have default cleanup cron expression")
        void shouldHaveDefaultCleanupCron() {
            assertThat(properties.getCleanupCron()).isEqualTo("0 0 3 * * ?");
        }

        @Test
        @DisplayName("Should have stuckThresholdMinutes=30 by default")
        void shouldHaveDefaultStuckThreshold() {
            assertThat(properties.getStuckThresholdMinutes()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should have empty excluded schedulers list by default")
        void shouldHaveEmptyExcludedSchedulers() {
            assertThat(properties.getExcludedSchedulers()).isEmpty();
        }

        @Test
        @DisplayName("Should have default lock configuration")
        void shouldHaveDefaultLockConfig() {
            SchedulerProperties.LockConfig lockConfig = properties.getLock();

            assertThat(lockConfig).isNotNull();
            assertThat(lockConfig.getLockAtMost()).isEqualTo(Duration.ofSeconds(60));
            assertThat(lockConfig.getLockAtLeast()).isEqualTo(Duration.ofSeconds(1));
            assertThat(lockConfig.getMaxRetries()).isEqualTo(3);
            assertThat(lockConfig.getRetryDelaysMs()).containsExactly(100, 200, 500);
        }
    }

    @Nested
    @DisplayName("Property setters")
    class SettersTest {

        @Test
        @DisplayName("Should set enabled property")
        void shouldSetEnabled() {
            properties.setEnabled(false);
            assertThat(properties.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should set aspect enabled property")
        void shouldSetAspectEnabled() {
            properties.setAspectEnabled(false);
            assertThat(properties.isAspectEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should set mode property")
        void shouldSetMode() {
            properties.setMode("in-memory");
            assertThat(properties.getMode()).isEqualTo("in-memory");
        }

        @Test
        @DisplayName("Should set retention days")
        void shouldSetRetentionDays() {
            properties.setRetentionDays(30);
            assertThat(properties.getRetentionDays()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should set cleanup cron")
        void shouldSetCleanupCron() {
            properties.setCleanupCron("0 0 1 * * ?");
            assertThat(properties.getCleanupCron()).isEqualTo("0 0 1 * * ?");
        }

        @Test
        @DisplayName("Should set stuck threshold minutes")
        void shouldSetStuckThreshold() {
            properties.setStuckThresholdMinutes(60);
            assertThat(properties.getStuckThresholdMinutes()).isEqualTo(60);
        }

        @Test
        @DisplayName("Should set excluded schedulers list")
        void shouldSetExcludedSchedulers() {
            properties.setExcludedSchedulers(List.of("CacheMetrics", "Health"));
            assertThat(properties.getExcludedSchedulers()).containsExactly("CacheMetrics", "Health");
        }
    }

    @Nested
    @DisplayName("isExcluded")
    class IsExcludedTest {

        @Test
        @DisplayName("Should return false when excluded list is empty")
        void shouldReturnFalseWhenListEmpty() {
            properties.setExcludedSchedulers(Collections.emptyList());

            assertThat(properties.isExcluded("AnyScheduler_run")).isFalse();
        }

        @Test
        @DisplayName("Should return false when excluded list is null")
        void shouldReturnFalseWhenListNull() {
            properties.setExcludedSchedulers(null);

            assertThat(properties.isExcluded("AnyScheduler_run")).isFalse();
        }

        @Test
        @DisplayName("Should return true when scheduler name starts with excluded pattern")
        void shouldReturnTrueWhenPrefixMatches() {
            properties.setExcludedSchedulers(List.of("CacheMetrics"));

            assertThat(properties.isExcluded("CacheMetricsCollector_collect")).isTrue();
        }

        @Test
        @DisplayName("Should return true when scheduler name exactly matches excluded pattern")
        void shouldReturnTrueWhenExactMatch() {
            properties.setExcludedSchedulers(List.of("CacheMetrics_collect"));

            assertThat(properties.isExcluded("CacheMetrics_collect")).isTrue();
        }

        @Test
        @DisplayName("Should return false when scheduler name does not match any pattern")
        void shouldReturnFalseWhenNoMatch() {
            properties.setExcludedSchedulers(List.of("CacheMetrics", "Health"));

            assertThat(properties.isExcluded("SyncJob_execute")).isFalse();
        }

        @Test
        @DisplayName("Should match against multiple exclusion patterns")
        void shouldMatchMultiplePatterns() {
            properties.setExcludedSchedulers(List.of("CacheMetrics", "Health", "Internal"));

            assertThat(properties.isExcluded("CacheMetricsJob_run")).isTrue();
            assertThat(properties.isExcluded("HealthCheck_check")).isTrue();
            assertThat(properties.isExcluded("InternalCleanup_clean")).isTrue();
            assertThat(properties.isExcluded("SyncJob_sync")).isFalse();
        }

        @Test
        @DisplayName("Should be case sensitive for prefix matching")
        void shouldBeCaseSensitive() {
            properties.setExcludedSchedulers(List.of("CacheMetrics"));

            assertThat(properties.isExcluded("cachemetrics_collect")).isFalse();
            assertThat(properties.isExcluded("CACHEMETRICS_collect")).isFalse();
        }
    }

    @Nested
    @DisplayName("LockConfig")
    class LockConfigTest {

        @Test
        @DisplayName("Should set lock at most duration")
        void shouldSetLockAtMost() {
            SchedulerProperties.LockConfig lockConfig = new SchedulerProperties.LockConfig();
            lockConfig.setLockAtMost(Duration.ofSeconds(120));

            assertThat(lockConfig.getLockAtMost()).isEqualTo(Duration.ofSeconds(120));
        }

        @Test
        @DisplayName("Should set lock at least duration")
        void shouldSetLockAtLeast() {
            SchedulerProperties.LockConfig lockConfig = new SchedulerProperties.LockConfig();
            lockConfig.setLockAtLeast(Duration.ofSeconds(5));

            assertThat(lockConfig.getLockAtLeast()).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("Should set max retries")
        void shouldSetMaxRetries() {
            SchedulerProperties.LockConfig lockConfig = new SchedulerProperties.LockConfig();
            lockConfig.setMaxRetries(5);

            assertThat(lockConfig.getMaxRetries()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should set retry delays")
        void shouldSetRetryDelays() {
            SchedulerProperties.LockConfig lockConfig = new SchedulerProperties.LockConfig();
            lockConfig.setRetryDelaysMs(new long[]{50, 100, 200, 500});

            assertThat(lockConfig.getRetryDelaysMs()).containsExactly(50, 100, 200, 500);
        }
    }
}
