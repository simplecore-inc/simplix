package dev.simplecore.simplix.messaging.subscriber;

import dev.simplecore.simplix.messaging.autoconfigure.MessagingProperties;
import dev.simplecore.simplix.messaging.broker.BrokerStrategy;
import dev.simplecore.simplix.messaging.broker.SubscribeRequest;
import dev.simplecore.simplix.messaging.broker.Subscription;
import dev.simplecore.simplix.messaging.core.Message;
import dev.simplecore.simplix.messaging.core.MessageAcknowledgment;
import dev.simplecore.simplix.messaging.core.MessageHeaders;
import dev.simplecore.simplix.messaging.core.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scans Spring beans for methods annotated with {@link MessageHandler} and
 * registers them as subscribers with the active {@link BrokerStrategy}.
 *
 * <p>This processor operates in three phases:
 * <ol>
 *   <li>{@link #postProcessAfterInitialization} - discovers annotated methods and stores registration info</li>
 *   <li>{@link #afterSingletonsInstantiated} - logs discovered handlers (no subscriptions yet)</li>
 *   <li>{@link #start} - creates subscriptions via the broker, optionally after a startup delay
 *       to allow SSE clients and downstream consumers to connect first</li>
 * </ol>
 *
 * <p>The startup delay is configurable via {@code simplix.messaging.subscriber-startup-delay}.
 * When set to a positive duration, the subscriber waits after the application is ready before
 * starting to consume messages from the broker. This prevents message loss when push-based
 * consumers (e.g., SSE clients) need time to reconnect after a server restart.
 *
 * <p>Auto-deserialization is performed based on the type parameter {@code T} of the handler method's
 * first {@code Message<T>} parameter:
 * <ul>
 *   <li>{@code byte[]} - raw bytes are passed through</li>
 *   <li>{@code String} - UTF-8 decoded</li>
 *   <li>Wire protobuf types (extends {@code com.squareup.wire.Message}) - decoded via ADAPTER reflection</li>
 *   <li>{@code Object} or unresolvable - raw bytes are passed through</li>
 * </ul>
 */
@Slf4j
public class MessageHandlerRegistrar
        implements BeanPostProcessor, SmartInitializingSingleton, SmartLifecycle {

    private static final String WIRE_MESSAGE_CLASS = "com.squareup.wire.Message";

    /**
     * Start in a late phase so SSE infrastructure and HTTP server are ready first.
     */
    private static final int LIFECYCLE_PHASE = Integer.MAX_VALUE - 100;

    // Lazy providers — resolved after all singletons are instantiated to avoid
    // pulling the entire Redis/Metrics dependency chain during BeanPostProcessor creation.
    private final ObjectProvider<BrokerStrategy> brokerStrategyProvider;
    private final ObjectProvider<IdempotentGuard> idempotentGuardProvider;
    private final ObjectProvider<MessagingProperties> propertiesProvider;

    private final Environment environment;
    private final List<HandlerRegistration> registrations = new ArrayList<>();
    private final List<Subscription> activeSubscriptions = new ArrayList<>();

    // Eagerly resolved from providers in afterSingletonsInstantiated()
    private BrokerStrategy brokerStrategy;
    private IdempotentGuard idempotentGuard;
    private MessagingProperties properties;

    private volatile boolean running = false;
    private ScheduledExecutorService startupScheduler;

    /**
     * Create a new registrar with lazy dependency resolution.
     *
     * <p>Dependencies are accepted as {@link ObjectProvider} to avoid early bean initialization
     * during the {@link BeanPostProcessor} creation phase. They are resolved in
     * {@link #afterSingletonsInstantiated()} when all beans are available.
     *
     * @param brokerStrategyProvider  lazy provider for the active broker strategy
     * @param environment             the Spring environment for placeholder resolution
     * @param idempotentGuardProvider lazy provider for the optional idempotent guard
     * @param propertiesProvider      lazy provider for messaging properties
     */
    public MessageHandlerRegistrar(ObjectProvider<BrokerStrategy> brokerStrategyProvider,
                                   Environment environment,
                                   ObjectProvider<IdempotentGuard> idempotentGuardProvider,
                                   ObjectProvider<MessagingProperties> propertiesProvider) {
        this.brokerStrategyProvider = brokerStrategyProvider;
        this.environment = environment;
        this.idempotentGuardProvider = idempotentGuardProvider;
        this.propertiesProvider = propertiesProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        for (Method method : targetClass.getDeclaredMethods()) {
            MessageHandler annotation = method.getAnnotation(MessageHandler.class);
            if (annotation != null) {
                validateHandlerMethod(method);
                registrations.add(new HandlerRegistration(bean, method, annotation));
                log.info("Discovered @MessageHandler: {}.{}() -> channel={}",
                        targetClass.getSimpleName(), method.getName(), annotation.channel());
            }
        }
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // Resolve lazy dependencies now that all singletons are available
        this.brokerStrategy = brokerStrategyProvider.getObject();
        this.idempotentGuard = idempotentGuardProvider.getIfAvailable();
        this.properties = propertiesProvider.getIfAvailable();

        if (registrations.isEmpty()) {
            log.info("No @MessageHandler methods found");
        } else {
            log.info("Discovered {} @MessageHandler method(s), will start during lifecycle phase",
                    registrations.size());
        }
    }

    // ---------------------------------------------------------------
    // SmartLifecycle
    // ---------------------------------------------------------------

    @Override
    public void start() {
        if (running || registrations.isEmpty()) {
            running = true;
            return;
        }

        running = true;

        Duration delay = properties != null ? properties.getSubscriberStartupDelay() : Duration.ZERO;

        if (!delay.isZero() && !delay.isNegative()) {
            log.info("Deferring subscriber startup by {} to allow downstream consumers to connect",
                    delay);

            startupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "msg-handler-startup");
                t.setDaemon(true);
                return t;
            });
            startupScheduler.schedule(this::startSubscriptions, delay.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            startSubscriptions();
        }
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;

        if (startupScheduler != null) {
            startupScheduler.shutdownNow();
            startupScheduler = null;
        }

        log.info("Shutting down MessageHandlerRegistrar, cancelling {} active subscription(s)",
                activeSubscriptions.size());
        for (Subscription subscription : activeSubscriptions) {
            try {
                if (subscription.isActive()) {
                    subscription.cancel();
                }
            } catch (Exception e) {
                log.warn("Error cancelling subscription [channel={}, group={}]: {}",
                        subscription.channel(), subscription.groupName(), e.getMessage());
            }
        }
        activeSubscriptions.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return LIFECYCLE_PHASE;
    }

    /**
     * Return the list of active subscriptions (for lifecycle management and testing).
     */
    public List<Subscription> getActiveSubscriptions() {
        return List.copyOf(activeSubscriptions);
    }

    // ---------------------------------------------------------------
    // Subscription startup
    // ---------------------------------------------------------------

    private void startSubscriptions() {
        log.info("Registering {} message handler(s) with broker '{}'",
                registrations.size(), brokerStrategy.name());

        for (HandlerRegistration registration : registrations) {
            createSubscription(registration);
        }
    }

    // ---------------------------------------------------------------
    // Subscription creation
    // ---------------------------------------------------------------

    private void createSubscription(HandlerRegistration registration) {
        MessageHandler annotation = registration.annotation();
        String channel = resolvePlaceholder(annotation.channel());
        String group = resolvePlaceholder(annotation.group());

        // Ensure consumer group exists if specified (non-blocking on failure)
        if (!group.isEmpty()) {
            try {
                brokerStrategy.ensureConsumerGroup(channel, group);
            } catch (Exception e) {
                log.warn("Failed to ensure consumer group for channel='{}' group='{}': {}. "
                        + "Subscription will proceed and retry via periodic recovery.",
                        channel, group, e.getMessage());
            }
        }

        // Create the message listener with deserialization and auto-ack
        MessageListener<byte[]> listener = createListener(registration, channel, group);

        for (int i = 0; i < annotation.concurrency(); i++) {
            String consumerName = group.isEmpty()
                    ? UUID.randomUUID().toString()
                    : group + "-" + i;

            SubscribeRequest request = SubscribeRequest.builder()
                    .channel(channel)
                    .groupName(group)
                    .consumerName(consumerName)
                    .listener(listener)
                    .build();

            try {
                Subscription subscription = brokerStrategy.subscribe(request);
                activeSubscriptions.add(subscription);

                log.info("Subscribed to channel='{}' group='{}' consumer='{}' handler={}.{}()",
                        channel, group, consumerName,
                        registration.bean().getClass().getSimpleName(),
                        registration.method().getName());
            } catch (Exception e) {
                log.warn("Failed to subscribe to channel='{}' group='{}' consumer='{}': {}. "
                        + "Will be retried via periodic recovery.",
                        channel, group, consumerName, e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------
    // Listener creation with deserialization
    // ---------------------------------------------------------------

    private MessageListener<byte[]> createListener(HandlerRegistration registration, String channel, String group) {
        Method method = registration.method();
        Object bean = registration.bean();
        MessageHandler annotation = registration.annotation();

        Type payloadType = resolvePayloadType(method);
        boolean acceptsAck = method.getParameterCount() >= 2;

        // Make accessible once at registration time, not on every message
        method.setAccessible(true);

        return (rawMessage, ack) -> {
            // Idempotent guard check
            if (annotation.idempotent()) {
                if (idempotentGuard == null) {
                    log.warn("Idempotent guard is not configured but idempotent=true on {}.{}(). "
                                    + "Proceeding without deduplication.",
                            bean.getClass().getSimpleName(), method.getName());
                } else {
                    String messageId = rawMessage.getHeaders().get(MessageHeaders.MESSAGE_ID)
                            .orElse(rawMessage.getMessageId());
                    if (!idempotentGuard.tryAcquire(channel, group, messageId)) {
                        log.debug("Duplicate message detected, skipping: channel={} messageId={}",
                                channel, messageId);
                        ack.ack();
                        return;
                    }
                }
            }

            // Deserialize payload
            Message<?> deserializedMessage = deserializeMessage(rawMessage, payloadType);

            try {
                if (acceptsAck) {
                    method.invoke(bean, deserializedMessage, ack);
                } else {
                    method.invoke(bean, deserializedMessage);
                }

                // Auto-ack on successful return
                if (annotation.autoAck()) {
                    ack.ack();
                }
            } catch (InvocationTargetException e) {
                log.error("Handler {}.{}() threw: {}",
                        bean.getClass().getSimpleName(), method.getName(),
                        e.getCause() != null ? e.getCause().toString() : e.toString(), e.getCause());
                throw new RuntimeException("Handler invocation failed", e.getCause());
            } catch (IllegalAccessException e) {
                log.error("Cannot access handler {}.{}()",
                        bean.getClass().getSimpleName(), method.getName(), e);
                throw new RuntimeException("Handler access failed", e);
            }
        };
    }

    // ---------------------------------------------------------------
    // Deserialization logic
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Message<?> deserializeMessage(Message<byte[]> rawMessage, Type payloadType) {
        byte[] rawPayload = rawMessage.getPayload();

        Object deserialized;
        if (payloadType == byte[].class || payloadType == Object.class) {
            deserialized = rawPayload;
        } else if (payloadType == String.class) {
            deserialized = new String(rawPayload, StandardCharsets.UTF_8);
        } else if (payloadType instanceof Class<?> clazz && isWireMessage(clazz)) {
            deserialized = decodeWireProtobuf(clazz, rawPayload);
        } else {
            // Unresolvable type - pass raw bytes
            deserialized = rawPayload;
        }

        return Message.builder()
                .messageId(rawMessage.getMessageId())
                .channel(rawMessage.getChannel())
                .payload(deserialized)
                .headers(rawMessage.getHeaders())
                .timestamp(rawMessage.getTimestamp())
                .build();
    }

    /**
     * Check if a class extends com.squareup.wire.Message by walking the class hierarchy.
     * Uses class name comparison to avoid hard dependency on Wire runtime.
     */
    private boolean isWireMessage(Class<?> clazz) {
        Class<?> current = clazz.getSuperclass();
        while (current != null) {
            if (WIRE_MESSAGE_CLASS.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    /**
     * Decode a Wire protobuf message using its ADAPTER static field.
     */
    private Object decodeWireProtobuf(Class<?> protoClass, byte[] bytes) {
        try {
            java.lang.reflect.Field adapterField = protoClass.getField("ADAPTER");
            Object adapter = adapterField.get(null);
            Method decodeMethod = adapter.getClass().getMethod("decode", byte[].class);
            return decodeMethod.invoke(adapter, (Object) bytes);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                    "No ADAPTER field found on " + protoClass.getName()
                    + ". Ensure this is a Wire-generated protobuf class.", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to decode protobuf message via ADAPTER for " + protoClass.getName(), e);
        }
    }

    // ---------------------------------------------------------------
    // Type resolution
    // ---------------------------------------------------------------

    /**
     * Extract the payload type T from the method's first parameter {@code Message<T>}.
     */
    private Type resolvePayloadType(Method method) {
        Type paramType = method.getGenericParameterTypes()[0];
        if (paramType instanceof ParameterizedType pt) {
            Type[] typeArgs = pt.getActualTypeArguments();
            if (typeArgs.length > 0) {
                return typeArgs[0];
            }
        }
        // Raw Message type or unresolvable - default to Object
        return Object.class;
    }

    // ---------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------

    private void validateHandlerMethod(Method method) {
        if (method.getParameterCount() < 1 || method.getParameterCount() > 2) {
            throw new IllegalStateException(
                    "@MessageHandler method must have 1 or 2 parameters: " + method);
        }

        Type firstParam = method.getGenericParameterTypes()[0];
        if (firstParam instanceof ParameterizedType pt) {
            if (!Message.class.equals(pt.getRawType())) {
                throw new IllegalStateException(
                        "@MessageHandler method's first parameter must be Message<T>: " + method);
            }
        } else if (firstParam instanceof Class<?> clazz) {
            if (!Message.class.equals(clazz)) {
                throw new IllegalStateException(
                        "@MessageHandler method's first parameter must be Message<T>: " + method);
            }
        }

        if (method.getParameterCount() == 2) {
            Class<?> secondParam = method.getParameterTypes()[1];
            if (!MessageAcknowledgment.class.isAssignableFrom(secondParam)) {
                throw new IllegalStateException(
                        "@MessageHandler method's second parameter must be MessageAcknowledgment: " + method);
            }
        }
    }

    // ---------------------------------------------------------------
    // Placeholder resolution
    // ---------------------------------------------------------------

    private String resolvePlaceholder(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return environment.resolvePlaceholders(value);
    }

    // ---------------------------------------------------------------
    // Registration record
    // ---------------------------------------------------------------

    private record HandlerRegistration(Object bean, Method method, MessageHandler annotation) {
    }
}
