package dev.simplecore.simplix.messaging.broker.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@DisplayName("KafkaSubscription inner class")
@ExtendWith(MockitoExtension.class)
class KafkaSubscriptionTest {

    @Mock
    private KafkaTemplate<String, byte[]> kafkaTemplate;

    @Mock
    private ConsumerFactory<String, byte[]> consumerFactory;

    @Test
    @DisplayName("should expose channel and groupName")
    @SuppressWarnings("unchecked")
    void shouldExposeChannelAndGroup() throws Exception {
        KafkaBrokerStrategy strategy = new KafkaBrokerStrategy(kafkaTemplate, consumerFactory);

        ConcurrentMessageListenerContainer<String, byte[]> container = mock(ConcurrentMessageListenerContainer.class);
        when(container.isRunning()).thenReturn(true);

        Class<?> subClass = Class.forName("dev.simplecore.simplix.messaging.broker.kafka.KafkaBrokerStrategy$KafkaSubscription");
        Constructor<?> ctor = subClass.getDeclaredConstructor(
                KafkaBrokerStrategy.class, String.class, String.class,
                ConcurrentMessageListenerContainer.class, String.class);
        ctor.setAccessible(true);

        Object subscription = ctor.newInstance(strategy, "my-channel", "my-group", container, "key-1");

        Method channelMethod = subClass.getMethod("channel");
        Method groupMethod = subClass.getMethod("groupName");
        Method isActiveMethod = subClass.getMethod("isActive");
        Method cancelMethod = subClass.getMethod("cancel");

        assertThat(channelMethod.invoke(subscription)).isEqualTo("my-channel");
        assertThat(groupMethod.invoke(subscription)).isEqualTo("my-group");
        assertThat((boolean) isActiveMethod.invoke(subscription)).isTrue();

        cancelMethod.invoke(subscription);
        assertThat((boolean) isActiveMethod.invoke(subscription)).isFalse();

        // Double cancel should be safe
        assertThatCode(() -> cancelMethod.invoke(subscription)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should return inactive when container is not running")
    @SuppressWarnings("unchecked")
    void shouldReturnInactiveWhenContainerNotRunning() throws Exception {
        KafkaBrokerStrategy strategy = new KafkaBrokerStrategy(kafkaTemplate, consumerFactory);

        ConcurrentMessageListenerContainer<String, byte[]> container = mock(ConcurrentMessageListenerContainer.class);
        when(container.isRunning()).thenReturn(false);

        Class<?> subClass = Class.forName("dev.simplecore.simplix.messaging.broker.kafka.KafkaBrokerStrategy$KafkaSubscription");
        Constructor<?> ctor = subClass.getDeclaredConstructor(
                KafkaBrokerStrategy.class, String.class, String.class,
                ConcurrentMessageListenerContainer.class, String.class);
        ctor.setAccessible(true);

        Object subscription = ctor.newInstance(strategy, "ch", "grp", container, "k");

        Method isActiveMethod = subClass.getMethod("isActive");
        assertThat((boolean) isActiveMethod.invoke(subscription)).isFalse();
    }
}
