package dev.simplecore.simplix.stream.autoconfigure;

import dev.simplecore.simplix.stream.admin.StreamAdminController;
import dev.simplecore.simplix.stream.admin.command.*;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import dev.simplecore.simplix.stream.monitoring.StreamHealthIndicator;
import dev.simplecore.simplix.stream.monitoring.StreamMetrics;
import dev.simplecore.simplix.stream.persistence.service.StreamStatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Optional;
import java.util.UUID;

/**
 * Auto-configuration for admin and monitoring features.
 * <p>
 * Configures admin controller, health indicator, metrics, and
 * distributed admin command processing.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "simplix.stream.enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXStreamAdminConfiguration {

    @Value("${simplix.stream.instance-id:#{T(java.util.UUID).randomUUID().toString().substring(0, 8)}}")
    private String instanceId;

    /**
     * Admin controller bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public StreamAdminController streamAdminController(
            SessionManager sessionManager,
            SessionRegistry sessionRegistry,
            SchedulerManager schedulerManager,
            BroadcastService broadcastService,
            StreamProperties properties,
            Optional<AdminCommandService> commandService,
            Optional<StreamStatisticsService> statisticsService) {

        log.info("Creating stream admin controller");
        StreamAdminController controller = new StreamAdminController(
                sessionManager,
                sessionRegistry,
                schedulerManager,
                broadcastService,
                properties,
                commandService,
                statisticsService
        );
        controller.setInstanceId(instanceId);
        return controller;
    }

    /**
     * Health indicator bean.
     */
    @Bean
    @ConditionalOnMissingBean(name = "streamHealthIndicator")
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
    public HealthIndicator streamHealthIndicator(
            SessionRegistry sessionRegistry,
            SchedulerManager schedulerManager,
            BroadcastService broadcastService,
            StreamProperties properties) {

        log.info("Creating stream health indicator");
        return new StreamHealthIndicator(sessionRegistry, schedulerManager, broadcastService, properties);
    }

    /**
     * Metrics bean.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    @ConditionalOnProperty(name = "simplix.stream.monitoring.metrics-enabled", havingValue = "true", matchIfMissing = true)
    public StreamMetrics streamMetrics(
            SessionRegistry sessionRegistry,
            SchedulerManager schedulerManager,
            StreamProperties properties) {

        StreamMetrics metrics = new StreamMetrics(sessionRegistry, schedulerManager, properties);
        log.info("Created stream metrics");
        return metrics;
    }

    /**
     * Configuration for database-based distributed admin commands.
     */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "simplix.stream.admin.enabled", havingValue = "true")
    @ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
    @ConditionalOnBean(AdminCommandRepository.class)
    public class DistributedAdminConfiguration {

        /**
         * Admin command service for queuing commands.
         */
        @Bean
        @ConditionalOnMissingBean
        public AdminCommandService adminCommandService(
                AdminCommandRepository commandRepository,
                StreamProperties properties) {

            log.info("Creating distributed admin command service");
            return new DefaultAdminCommandService(commandRepository, properties.getAdmin().isEnabled());
        }

        /**
         * Admin command processor for polling and executing commands.
         */
        @Bean
        @ConditionalOnMissingBean
        public AdminCommandProcessor adminCommandProcessor(
                AdminCommandRepository commandRepository,
                SessionManager sessionManager,
                SessionRegistry sessionRegistry,
                SchedulerManager schedulerManager,
                StreamProperties properties) {

            log.info("Creating admin command processor with instance ID: {}", instanceId);
            return new AdminCommandProcessor(
                    commandRepository,
                    sessionManager,
                    sessionRegistry,
                    schedulerManager,
                    properties,
                    instanceId
            );
        }
    }
}
