package dev.simplecore.simplix.messaging.broker.rabbit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("RabbitSubscription inner class")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RabbitSubscriptionTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ConnectionFactory connectionFactory;

    @Test
    @DisplayName("should expose channel and groupName")
    void shouldExposeChannelAndGroup() throws Exception {
        Connection mockConnection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(mockConnection);
        com.rabbitmq.client.Channel mockChannel = mock(com.rabbitmq.client.Channel.class);
        when(mockConnection.createChannel(any(Boolean.class))).thenReturn(mockChannel);

        RabbitBrokerStrategy strategy = new RabbitBrokerStrategy(rabbitTemplate, connectionFactory);

        SimpleMessageListenerContainer container = mock(SimpleMessageListenerContainer.class);
        when(container.isRunning()).thenReturn(true);

        Class<?> subClass = Class.forName("dev.simplecore.simplix.messaging.broker.rabbit.RabbitBrokerStrategy$RabbitSubscription");
        Constructor<?> ctor = subClass.getDeclaredConstructor(
                RabbitBrokerStrategy.class, String.class, String.class,
                SimpleMessageListenerContainer.class, String.class);
        ctor.setAccessible(true);

        Object subscription = ctor.newInstance(strategy, "my-queue", "my-group", container, "key-1");

        Method channelMethod = subClass.getMethod("channel");
        Method groupMethod = subClass.getMethod("groupName");
        Method isActiveMethod = subClass.getMethod("isActive");
        Method cancelMethod = subClass.getMethod("cancel");

        assertThat(channelMethod.invoke(subscription)).isEqualTo("my-queue");
        assertThat(groupMethod.invoke(subscription)).isEqualTo("my-group");
        assertThat((boolean) isActiveMethod.invoke(subscription)).isTrue();

        cancelMethod.invoke(subscription);
        assertThat((boolean) isActiveMethod.invoke(subscription)).isFalse();

        // Double cancel should be safe
        assertThatCode(() -> cancelMethod.invoke(subscription)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should return inactive when container is not running")
    void shouldReturnInactiveWhenContainerNotRunning() throws Exception {
        Connection mockConnection = mock(Connection.class);
        when(connectionFactory.createConnection()).thenReturn(mockConnection);
        com.rabbitmq.client.Channel mockChannel = mock(com.rabbitmq.client.Channel.class);
        when(mockConnection.createChannel(any(Boolean.class))).thenReturn(mockChannel);

        RabbitBrokerStrategy strategy = new RabbitBrokerStrategy(rabbitTemplate, connectionFactory);

        SimpleMessageListenerContainer container = mock(SimpleMessageListenerContainer.class);
        when(container.isRunning()).thenReturn(false);

        Class<?> subClass = Class.forName("dev.simplecore.simplix.messaging.broker.rabbit.RabbitBrokerStrategy$RabbitSubscription");
        Constructor<?> ctor = subClass.getDeclaredConstructor(
                RabbitBrokerStrategy.class, String.class, String.class,
                SimpleMessageListenerContainer.class, String.class);
        ctor.setAccessible(true);

        Object subscription = ctor.newInstance(strategy, "q", "g", container, "k");

        Method isActiveMethod = subClass.getMethod("isActive");
        assertThat((boolean) isActiveMethod.invoke(subscription)).isFalse();
    }
}
