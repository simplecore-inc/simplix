package dev.simplecore.simplix.sync.autoconfigure;

import dev.simplecore.simplix.sync.core.InstanceSyncBroadcaster;
import dev.simplecore.simplix.sync.core.NoOpInstanceSyncBroadcaster;
import dev.simplecore.simplix.sync.infrastructure.redis.RedisInstanceSyncBroadcaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SimpliXSyncAutoConfiguration")
class SimpliXSyncAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpliXSyncAutoConfiguration.class));

    @Nested
    @DisplayName("noOpInstanceSyncBroadcaster")
    class NoOpBroadcasterTests {

        @Test
        @DisplayName("should create NoOpInstanceSyncBroadcaster bean by default")
        void shouldCreateNoOpBroadcaster() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(InstanceSyncBroadcaster.class);
                assertThat(context.getBean(InstanceSyncBroadcaster.class))
                        .isInstanceOf(NoOpInstanceSyncBroadcaster.class);
            });
        }

        @Test
        @DisplayName("should create NoOpInstanceSyncBroadcaster when mode is LOCAL")
        void shouldCreateNoOpBroadcasterInLocalMode() {
            contextRunner
                    .withPropertyValues("simplix.sync.mode=LOCAL")
                    .run(context -> {
                        assertThat(context).hasSingleBean(InstanceSyncBroadcaster.class);
                        assertThat(context.getBean(InstanceSyncBroadcaster.class))
                                .isInstanceOf(NoOpInstanceSyncBroadcaster.class);
                    });
        }

        @Test
        @DisplayName("should return non-null broadcaster")
        void shouldReturnNonNullBroadcaster() {
            SimpliXSyncAutoConfiguration config = new SimpliXSyncAutoConfiguration();
            InstanceSyncBroadcaster broadcaster = config.noOpInstanceSyncBroadcaster();
            assertThat(broadcaster).isNotNull();
        }
    }

    @Nested
    @DisplayName("Disabled module")
    class DisabledModuleTests {

        @Test
        @DisplayName("should not create beans when simplix.sync.enabled is false")
        void shouldNotCreateBeansWhenDisabled() {
            contextRunner
                    .withPropertyValues("simplix.sync.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(InstanceSyncBroadcaster.class);
                    });
        }
    }

    @Nested
    @DisplayName("RedisConfiguration")
    class RedisConfigurationTests {

        @Test
        @DisplayName("should create RedisInstanceSyncBroadcaster when mode is DISTRIBUTED and Redis is available")
        void shouldCreateRedisBroadcasterInDistributedMode() {
            RedisConnectionFactory mockFactory = mock(RedisConnectionFactory.class);
            RedisConnection mockConnection = mock(RedisConnection.class);
            RedisServerCommands mockServerCommands = mock(RedisServerCommands.class);
            when(mockFactory.getConnection()).thenReturn(mockConnection);
            when(mockConnection.serverCommands()).thenReturn(mockServerCommands);
            when(mockServerCommands.info()).thenReturn(new Properties());

            contextRunner
                    .withPropertyValues("simplix.sync.mode=DISTRIBUTED")
                    .withBean(RedisConnectionFactory.class, () -> mockFactory)
                    .run(context -> {
                        assertThat(context).hasSingleBean(InstanceSyncBroadcaster.class);
                        assertThat(context.getBean(InstanceSyncBroadcaster.class))
                                .isInstanceOf(RedisInstanceSyncBroadcaster.class);
                    });
        }

        @Test
        @DisplayName("should fall back to NoOp when DISTRIBUTED mode but no RedisConnectionFactory bean")
        void shouldFallBackToNoOpWhenNoRedisBean() {
            contextRunner
                    .withPropertyValues("simplix.sync.mode=DISTRIBUTED")
                    .run(context -> {
                        // Without RedisAutoConfiguration, no RedisConnectionFactory bean exists
                        // so ConditionalOnBean(RedisConnectionFactory.class) fails
                        // and NoOp fallback should be created
                        assertThat(context).hasSingleBean(InstanceSyncBroadcaster.class);
                        assertThat(context.getBean(InstanceSyncBroadcaster.class))
                                .isInstanceOf(NoOpInstanceSyncBroadcaster.class);
                    });
        }
    }
}
