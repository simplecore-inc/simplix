package dev.simplecore.simplix.scheduler.config;

import dev.simplecore.simplix.scheduler.aspect.SchedulerExecutionAspect;
import dev.simplecore.simplix.scheduler.core.SchedulerExecutionLogProvider;
import dev.simplecore.simplix.scheduler.core.SchedulerLoggingService;
import dev.simplecore.simplix.scheduler.core.SchedulerLoggingStrategy;
import dev.simplecore.simplix.scheduler.core.SchedulerMetadata;
import dev.simplecore.simplix.scheduler.core.SchedulerRegistryProvider;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionContext;
import dev.simplecore.simplix.scheduler.model.SchedulerExecutionResult;
import dev.simplecore.simplix.scheduler.model.SchedulerRegistryEntry;
import dev.simplecore.simplix.scheduler.service.DefaultSchedulerLoggingService;
import dev.simplecore.simplix.scheduler.strategy.DatabaseLoggingStrategy;
import dev.simplecore.simplix.scheduler.strategy.InMemoryLoggingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchedulerAutoConfiguration - Auto-configuration of scheduler logging beans")
class SchedulerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SchedulerAutoConfiguration.class));

    @Nested
    @DisplayName("Default configuration")
    class DefaultConfigTest {

        @Test
        @DisplayName("Should create SchedulerLoggingService bean")
        void shouldCreateLoggingServiceBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(SchedulerLoggingService.class);
                assertThat(context.getBean(SchedulerLoggingService.class))
                    .isInstanceOf(DefaultSchedulerLoggingService.class);
            });
        }

        @Test
        @DisplayName("Should create InMemoryLoggingStrategy bean")
        void shouldCreateInMemoryStrategyBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(InMemoryLoggingStrategy.class);
            });
        }

        @Test
        @DisplayName("Should create SchedulerExecutionAspect bean")
        void shouldCreateAspectBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(SchedulerExecutionAspect.class);
            });
        }

        @Test
        @DisplayName("Should create SchedulerProperties bean")
        void shouldCreatePropertiesBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(SchedulerProperties.class);
            });
        }
    }

    @Nested
    @DisplayName("Disabled configuration")
    class DisabledConfigTest {

        @Test
        @DisplayName("Should not create beans when scheduler is disabled")
        void shouldNotCreateBeansWhenDisabled() {
            contextRunner
                .withPropertyValues("simplix.scheduler.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SchedulerLoggingService.class);
                    assertThat(context).doesNotHaveBean(SchedulerExecutionAspect.class);
                    assertThat(context).doesNotHaveBean(InMemoryLoggingStrategy.class);
                });
        }
    }

    @Nested
    @DisplayName("Aspect disabled configuration")
    class AspectDisabledConfigTest {

        @Test
        @DisplayName("Should not create aspect bean when aspect is disabled")
        void shouldNotCreateAspectWhenDisabled() {
            contextRunner
                .withPropertyValues("simplix.scheduler.aspect-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SchedulerExecutionAspect.class);
                    // Other beans should still be created
                    assertThat(context).hasSingleBean(SchedulerLoggingService.class);
                    assertThat(context).hasSingleBean(InMemoryLoggingStrategy.class);
                });
        }
    }

    @Nested
    @DisplayName("Custom bean override")
    class CustomBeanOverrideTest {

        @Test
        @DisplayName("Should not create default SchedulerLoggingService when custom one exists")
        void shouldNotOverrideCustomLoggingService() {
            contextRunner
                .withUserConfiguration(CustomLoggingServiceConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SchedulerLoggingService.class);
                    assertThat(context.getBean(SchedulerLoggingService.class))
                        .isInstanceOf(CustomSchedulerLoggingService.class);
                });
        }
    }

    @Nested
    @DisplayName("Database strategy configuration")
    class DatabaseStrategyConfigTest {

        @Test
        @DisplayName("Should not create DatabaseLoggingStrategy when providers are missing")
        void shouldNotCreateDatabaseStrategyWithoutProviders() {
            contextRunner.run(context -> {
                assertThat(context).doesNotHaveBean(DatabaseLoggingStrategy.class);
            });
        }
    }

    @Nested
    @DisplayName("Property binding")
    class PropertyBindingTest {

        @Test
        @DisplayName("Should bind mode property")
        void shouldBindModeProperty() {
            contextRunner
                .withPropertyValues("simplix.scheduler.mode=in-memory")
                .run(context -> {
                    SchedulerProperties props = context.getBean(SchedulerProperties.class);
                    assertThat(props.getMode()).isEqualTo("in-memory");
                });
        }

        @Test
        @DisplayName("Should bind retention-days property")
        void shouldBindRetentionDays() {
            contextRunner
                .withPropertyValues("simplix.scheduler.retention-days=30")
                .run(context -> {
                    SchedulerProperties props = context.getBean(SchedulerProperties.class);
                    assertThat(props.getRetentionDays()).isEqualTo(30);
                });
        }

        @Test
        @DisplayName("Should bind excluded-schedulers property")
        void shouldBindExcludedSchedulers() {
            contextRunner
                .withPropertyValues(
                    "simplix.scheduler.excluded-schedulers[0]=CacheMetrics",
                    "simplix.scheduler.excluded-schedulers[1]=Health"
                )
                .run(context -> {
                    SchedulerProperties props = context.getBean(SchedulerProperties.class);
                    assertThat(props.getExcludedSchedulers()).containsExactly("CacheMetrics", "Health");
                });
        }
    }

    // Custom configurations for testing

    @Configuration
    static class CustomLoggingServiceConfig {
        @Bean
        public SchedulerLoggingService schedulerLoggingService() {
            return new CustomSchedulerLoggingService();
        }
    }

    static class CustomSchedulerLoggingService implements SchedulerLoggingService {
        @Override
        public SchedulerRegistryEntry ensureRegistryEntry(SchedulerMetadata metadata) {
            return SchedulerRegistryEntry.fromMetadata(metadata);
        }

        @Override
        public SchedulerExecutionContext createExecutionContext(
            SchedulerRegistryEntry registry, String serviceName) {
            return SchedulerExecutionContext.builder().build();
        }

        @Override
        public void saveExecutionResult(
            SchedulerExecutionContext context, SchedulerExecutionResult result) {
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isExcluded(String schedulerName) {
            return false;
        }

        @Override
        public void clearCache() {
        }
    }
}
