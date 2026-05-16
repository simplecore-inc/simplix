package dev.simplecore.simplix.sync.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SyncProperties")
class SyncPropertiesTest {

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        @DisplayName("should be enabled by default")
        void shouldBeEnabledByDefault() {
            SyncProperties properties = new SyncProperties();
            assertThat(properties.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should default to LOCAL mode")
        void shouldDefaultToLocalMode() {
            SyncProperties properties = new SyncProperties();
            assertThat(properties.getMode()).isEqualTo(SyncProperties.Mode.LOCAL);
        }

        @Test
        @DisplayName("should have distributed settings initialized")
        void shouldHaveDistributedSettings() {
            SyncProperties properties = new SyncProperties();
            assertThat(properties.getDistributed()).isNotNull();
            assertThat(properties.getDistributed().isRedisEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("Setter methods")
    class SetterMethods {

        @Test
        @DisplayName("should set enabled to false")
        void shouldSetEnabled() {
            SyncProperties properties = new SyncProperties();
            properties.setEnabled(false);
            assertThat(properties.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should set mode to DISTRIBUTED")
        void shouldSetModeToDistributed() {
            SyncProperties properties = new SyncProperties();
            properties.setMode(SyncProperties.Mode.DISTRIBUTED);
            assertThat(properties.getMode()).isEqualTo(SyncProperties.Mode.DISTRIBUTED);
        }

        @Test
        @DisplayName("should set distributed settings")
        void shouldSetDistributedSettings() {
            SyncProperties properties = new SyncProperties();
            SyncProperties.Distributed distributed = new SyncProperties.Distributed();
            distributed.setRedisEnabled(false);
            properties.setDistributed(distributed);
            assertThat(properties.getDistributed().isRedisEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Mode enum")
    class ModeEnumTests {

        @Test
        @DisplayName("should have LOCAL and DISTRIBUTED values")
        void shouldHaveExpectedValues() {
            assertThat(SyncProperties.Mode.values())
                    .containsExactly(SyncProperties.Mode.LOCAL, SyncProperties.Mode.DISTRIBUTED);
        }
    }

    @Nested
    @DisplayName("Broker enum")
    class BrokerEnumTests {

        @Test
        @DisplayName("should default to REDIS for backward compatibility")
        void shouldDefaultToRedis() {
            SyncProperties properties = new SyncProperties();
            assertThat(properties.getDistributed().getBroker())
                    .isEqualTo(SyncProperties.Broker.REDIS);
        }

        @Test
        @DisplayName("should be settable to NATS")
        void shouldBeSettableToNats() {
            SyncProperties properties = new SyncProperties();
            properties.getDistributed().setBroker(SyncProperties.Broker.NATS);
            assertThat(properties.getDistributed().getBroker())
                    .isEqualTo(SyncProperties.Broker.NATS);
        }

        @Test
        @DisplayName("should expose REDIS and NATS values")
        void shouldExposeExpectedValues() {
            assertThat(SyncProperties.Broker.values())
                    .containsExactly(SyncProperties.Broker.REDIS, SyncProperties.Broker.NATS);
        }
    }
}
