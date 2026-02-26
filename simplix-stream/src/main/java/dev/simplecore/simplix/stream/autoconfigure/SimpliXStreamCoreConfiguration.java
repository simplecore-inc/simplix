package dev.simplecore.simplix.stream.autoconfigure;

import dev.simplecore.simplix.stream.collector.SimpliXStreamDataCollectorRegistry;
import dev.simplecore.simplix.stream.config.StreamProperties;
import dev.simplecore.simplix.stream.core.broadcast.BroadcastService;
import dev.simplecore.simplix.stream.core.scheduler.SchedulerManager;
import dev.simplecore.simplix.stream.core.session.SessionManager;
import dev.simplecore.simplix.stream.core.session.SessionRegistry;
import dev.simplecore.simplix.stream.core.subscription.SubscriptionManager;
import dev.simplecore.simplix.stream.eventsource.EventStreamHandler;
import dev.simplecore.simplix.stream.eventsource.SimpliXStreamEventSourceRegistry;
import dev.simplecore.simplix.stream.infrastructure.distributed.RedisLeaderElection;
import dev.simplecore.simplix.stream.infrastructure.local.LocalBroadcaster;
import dev.simplecore.simplix.stream.infrastructure.local.LocalSessionRegistry;
import dev.simplecore.simplix.stream.persistence.service.DbSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Core configuration for SimpliX Stream components.
 * <p>
 * Configures session management, subscription management, scheduling,
 * and broadcasting for local mode.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "simplix.stream.enabled", havingValue = "true", matchIfMissing = true)
public class SimpliXStreamCoreConfiguration {

    /**
     * Scheduled executor service for schedulers and timers.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "streamScheduledExecutor")
    public ScheduledExecutorService streamScheduledExecutor(StreamProperties properties) {
        int poolSize = properties.getScheduler().getThreadPoolSize();
        log.info("Creating stream scheduled executor with pool size: {}", poolSize);
        return Executors.newScheduledThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "stream-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Local mode configuration.
     */
    @Configuration
    @ConditionalOnProperty(name = "simplix.stream.mode", havingValue = "local", matchIfMissing = true)
    public static class LocalModeConfiguration {

        @Bean
        @ConditionalOnMissingBean(SessionRegistry.class)
        public SessionRegistry localSessionRegistry() {
            LocalSessionRegistry registry = new LocalSessionRegistry();
            registry.initialize();
            return registry;
        }

        @Bean
        @ConditionalOnMissingBean(BroadcastService.class)
        public LocalBroadcaster localBroadcaster() {
            LocalBroadcaster broadcaster = new LocalBroadcaster();
            broadcaster.initialize();
            return broadcaster;
        }
    }

    /**
     * Session manager bean.
     * <p>
     * When DbSessionRegistry is available, it enables cross-server session
     * restoration for reconnection scenarios.
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(
            SessionRegistry sessionRegistry,
            StreamProperties properties,
            ScheduledExecutorService streamScheduledExecutor,
            @Autowired(required = false) DbSessionRegistry dbSessionRegistry) {
        return new SessionManager(sessionRegistry, properties, streamScheduledExecutor, dbSessionRegistry);
    }

    /**
     * Subscription manager bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public SubscriptionManager subscriptionManager(
            SessionManager sessionManager,
            StreamProperties properties) {
        return new SubscriptionManager(sessionManager, properties);
    }

    /**
     * Scheduler manager bean.
     * <p>
     * In distributed mode, integrates with RedisLeaderElection to ensure
     * only one instance runs the scheduler for each subscription key.
     */
    @Bean
    @ConditionalOnMissingBean
    public SchedulerManager schedulerManager(
            SimpliXStreamDataCollectorRegistry collectorRegistry,
            BroadcastService broadcastService,
            StreamProperties properties,
            ScheduledExecutorService streamScheduledExecutor,
            @Autowired(required = false) RedisLeaderElection leaderElection) {
        return new SchedulerManager(
                collectorRegistry, broadcastService, properties, streamScheduledExecutor, leaderElection);
    }

    /**
     * Wire subscription manager callbacks to scheduler manager and event stream handler.
     */
    @Bean
    public StreamComponentWiring streamComponentWiring(
            SubscriptionManager subscriptionManager,
            SchedulerManager schedulerManager,
            SessionManager sessionManager,
            StreamProperties properties,
            @Autowired(required = false) EventStreamHandler eventStreamHandler,
            @Autowired(required = false) SimpliXStreamEventSourceRegistry eventSourceRegistry) {
        return new StreamComponentWiring(
                subscriptionManager, schedulerManager, sessionManager, properties,
                eventStreamHandler, eventSourceRegistry);
    }

    /**
     * Wires stream components together after construction.
     * <p>
     * Routes subscriptions to either SchedulerManager (polling) or EventStreamHandler (event-based)
     * depending on whether a StreamEventSource is registered for the resource.
     */
    @Slf4j
    public static class StreamComponentWiring {

        public StreamComponentWiring(
                SubscriptionManager subscriptionManager,
                SchedulerManager schedulerManager,
                SessionManager sessionManager,
                StreamProperties properties,
                EventStreamHandler eventStreamHandler,
                SimpliXStreamEventSourceRegistry eventSourceRegistry) {

            boolean eventSourceEnabled = eventStreamHandler != null && eventSourceRegistry != null;

            // Wire subscription events - route based on resource type
            subscriptionManager.setOnSubscriptionAdded((key, sessionId) -> {
                String resource = key.getResource();

                // Check if this is an event-based resource
                if (eventSourceEnabled && eventSourceRegistry.hasEventSource(resource)) {
                    // Route to event stream handler (no scheduler needed)
                    eventStreamHandler.addSubscriber(key, sessionId);
                    log.debug("Subscription routed to event handler: {} -> {}",
                            sessionId, key.toKeyString());
                } else {
                    // Route to scheduler manager (polling-based)
                    schedulerManager.addSubscriber(key, sessionId, properties.getScheduler().getDefaultInterval());
                    log.debug("Subscription routed to scheduler: {} -> {}",
                            sessionId, key.toKeyString());
                }
            });

            subscriptionManager.setOnSubscriptionRemoved((key, sessionId) -> {
                String resource = key.getResource();

                // Route removal based on resource type
                if (eventSourceEnabled && eventSourceRegistry.hasEventSource(resource)) {
                    eventStreamHandler.removeSubscriber(key, sessionId);
                } else {
                    schedulerManager.removeSubscriber(key, sessionId);
                }
            });

            // Wire session termination to subscription cleanup
            sessionManager.setOnSessionTerminated(session -> {
                for (var key : session.getSubscriptions()) {
                    String resource = key.getResource();

                    if (eventSourceEnabled && eventSourceRegistry.hasEventSource(resource)) {
                        eventStreamHandler.removeSubscriber(key, session.getId());
                    } else {
                        schedulerManager.removeSubscriber(key, session.getId());
                    }
                }

                // Also clean up from event subscriber registry if enabled
                if (eventSourceEnabled) {
                    eventStreamHandler.removeSubscriberFromAll(session.getId());
                }
            });

            if (eventSourceEnabled) {
                log.info("Stream components wired with event source support (resources: {})",
                        eventSourceRegistry.getRegisteredResources());
            } else {
                log.info("Stream components wired (event source not enabled)");
            }
        }
    }
}
