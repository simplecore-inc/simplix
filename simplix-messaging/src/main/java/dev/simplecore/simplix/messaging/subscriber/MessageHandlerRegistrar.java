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
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Scans Spring beans for methods annotated with {@link MessageHandler} and
 * registers them as subscribers with the active {@link BrokerStrategy}.
 *
 * <p>This processor operates in two phases:
 * <ol>
 *   <li>{@link #postProcessAfterInitialization} - discovers annotated methods and stores registration info</li>
 *   <li>{@link #afterSingletonsInstantiated} - creates subscriptions via the broker for all discovered handlers</li>
 * </ol>
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
public class MessageHandlerRegistrar implements BeanPostProcessor, SmartInitializingSingleton, DisposableBean {

    private static final String WIRE_MESSAGE_CLASS = "com.squareup.wire.Message";

    private final BrokerStrategy brokerStrategy;
    private final Environment environment;
    private final IdempotentGuard idempotentGuard;
    private final MessagingProperties properties;
    private final List<HandlerRegistration> registrations = new ArrayList<>();
    private final List<Subscription> activeSubscriptions = new ArrayList<>();

    /**
     * Create a new registrar.
     *
     * @param brokerStrategy  the active broker strategy
     * @param environment     the Spring environment for placeholder resolution
     * @param idempotentGuard optional idempotent guard for deduplication
     * @param properties      the messaging properties (can be {@code null} for testing)
     */
    public MessageHandlerRegistrar(BrokerStrategy brokerStrategy,
                                   Environment environment,
                                   Optional<IdempotentGuard> idempotentGuard,
                                   MessagingProperties properties) {
        this.brokerStrategy = brokerStrategy;
        this.environment = environment;
        this.idempotentGuard = idempotentGuard.orElse(null);
        this.properties = properties;
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
        if (registrations.isEmpty()) {
            log.info("No @MessageHandler methods found");
            return;
        }

        log.info("Registering {} message handler(s) with broker '{}'",
                registrations.size(), brokerStrategy.name());

        for (HandlerRegistration registration : registrations) {
            createSubscription(registration);
        }
    }

    /**
     * Return the list of active subscriptions (for lifecycle management and testing).
     */
    public List<Subscription> getActiveSubscriptions() {
        return List.copyOf(activeSubscriptions);
    }

    @Override
    public void destroy() {
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

    // ---------------------------------------------------------------
    // Subscription creation
    // ---------------------------------------------------------------

    private void createSubscription(HandlerRegistration registration) {
        MessageHandler annotation = registration.annotation();
        String channel = resolvePlaceholder(annotation.channel());
        String group = resolvePlaceholder(annotation.group());

        // Ensure consumer group exists if specified
        if (!group.isEmpty()) {
            brokerStrategy.ensureConsumerGroup(channel, group);
        }

        // Create the message listener with deserialization and auto-ack
        MessageListener<byte[]> listener = createListener(registration, channel);

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

            Subscription subscription = brokerStrategy.subscribe(request);
            activeSubscriptions.add(subscription);

            log.info("Subscribed to channel='{}' group='{}' consumer='{}' handler={}.{}()",
                    channel, group, consumerName,
                    registration.bean().getClass().getSimpleName(),
                    registration.method().getName());
        }
    }

    // ---------------------------------------------------------------
    // Listener creation with deserialization
    // ---------------------------------------------------------------

    private MessageListener<byte[]> createListener(HandlerRegistration registration, String channel) {
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
                    if (!idempotentGuard.tryAcquire(channel, messageId)) {
                        log.debug("Duplicate message detected, skipping: channel={} messageId={}",
                                channel, messageId);
                        ack.ack();
                        return;
                    }
                }
            }

            // Deserialize payload
            Message<?> deserializedMessage = deserializeMessage(rawMessage, payloadType);

            // Invoke handler
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
                log.error("Handler {}.{}() threw exception for message on channel='{}'",
                        bean.getClass().getSimpleName(), method.getName(), channel, e.getCause());
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
