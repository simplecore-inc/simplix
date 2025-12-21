package dev.simplecore.simplix.scheduler.config;

import dev.simplecore.simplix.scheduler.aspect.SchedulerExecutionAspect;
import dev.simplecore.simplix.scheduler.core.SchedulerExecutionLogProvider;
import dev.simplecore.simplix.scheduler.core.SchedulerLoggingService;
import dev.simplecore.simplix.scheduler.core.SchedulerLoggingStrategy;
import dev.simplecore.simplix.scheduler.core.SchedulerRegistryProvider;
import dev.simplecore.simplix.scheduler.service.DefaultSchedulerLoggingService;
import dev.simplecore.simplix.scheduler.strategy.DatabaseLoggingStrategy;
import dev.simplecore.simplix.scheduler.strategy.InMemoryLoggingStrategy;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Optional;

/**
 * Auto-configuration for SimpliX Scheduler Logging.
 * <p>
 * Automatically configures scheduler execution logging based on available beans
 * and properties.
 */
@AutoConfiguration
@EnableConfigurationProperties(SchedulerProperties.class)
@ConditionalOnProperty(name = "simplix.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class SchedulerAutoConfiguration {

    /**
     * Main logging service bean
     */
    @Bean
    @ConditionalOnMissingBean(SchedulerLoggingService.class)
    public SchedulerLoggingService schedulerLoggingService(
        List<SchedulerLoggingStrategy> strategies,
        SchedulerProperties properties
    ) {
        log.info("Configuring SchedulerLoggingService with mode: {}", properties.getMode());
        return new DefaultSchedulerLoggingService(strategies, properties);
    }

    /**
     * AOP aspect for @Scheduled method interception
     */
    @Bean
    @ConditionalOnMissingBean(SchedulerExecutionAspect.class)
    @ConditionalOnProperty(name = "simplix.scheduler.aspect-enabled", havingValue = "true", matchIfMissing = true)
    public SchedulerExecutionAspect schedulerExecutionAspect(
        SchedulerLoggingService loggingService,
        SchedulerProperties properties,
        Environment environment,
        @Value("${spring.application.name:unknown-service}") String serviceName
    ) {
        log.debug("Registering SchedulerExecutionAspect for service: {}", serviceName);
        return new SchedulerExecutionAspect(loggingService, properties, serviceName, environment);
    }

    /**
     * In-memory strategy - always available as fallback
     */
    @Bean
    @ConditionalOnMissingBean(InMemoryLoggingStrategy.class)
    public InMemoryLoggingStrategy inMemoryLoggingStrategy() {
        log.debug("Registering InMemoryLoggingStrategy");
        return new InMemoryLoggingStrategy();
    }

    /**
     * Database strategy configuration
     */
    @Configuration
    @ConditionalOnBean({SchedulerRegistryProvider.class, SchedulerExecutionLogProvider.class})
    public static class DatabaseStrategyConfiguration {

        @Bean
        @ConditionalOnMissingBean(DatabaseLoggingStrategy.class)
        @SuppressWarnings("rawtypes")
        public DatabaseLoggingStrategy databaseLoggingStrategy(
            SchedulerRegistryProvider registryProvider,
            SchedulerExecutionLogProvider logProvider,
            Optional<LockProvider> lockProvider,
            SchedulerProperties properties
        ) {
            log.info("Configuring DatabaseLoggingStrategy with LockProvider: {}",
                lockProvider.isPresent() ? "available" : "not available");

            return new DatabaseLoggingStrategy(
                registryProvider,
                logProvider,
                lockProvider.orElse(null),
                properties
            );
        }
    }
}
