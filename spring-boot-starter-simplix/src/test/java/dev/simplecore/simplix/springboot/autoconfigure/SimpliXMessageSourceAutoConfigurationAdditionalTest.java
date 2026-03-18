package dev.simplecore.simplix.springboot.autoconfigure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.context.MessageSourceProperties;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimpliXMessageSourceAutoConfiguration - additional coverage tests")
class SimpliXMessageSourceAutoConfigurationAdditionalTest {

    @Mock
    private Environment environment;

    @Nested
    @DisplayName("messageSourceProperties")
    class MessageSourcePropertiesTests {

        @Test
        @DisplayName("Should use configured basenames when spring.messages.basename is set")
        void configuredBasenames() {
            when(environment.getProperty("spring.messages.basename"))
                    .thenReturn("messages/custom,messages/other");

            SimpliXMessageSourceAutoConfiguration config = new SimpliXMessageSourceAutoConfiguration();
            MessageSourceProperties props = config.messageSourceProperties(environment);

            assertThat(props.getBasename()).containsExactly("messages/custom", "messages/other");
        }

        @Test
        @DisplayName("Should use default basenames when spring.messages.basename is not set")
        void defaultBasenames() {
            when(environment.getProperty("spring.messages.basename")).thenReturn(null);

            SimpliXMessageSourceAutoConfiguration config = new SimpliXMessageSourceAutoConfiguration();
            MessageSourceProperties props = config.messageSourceProperties(environment);

            assertThat(props.getBasename()).containsExactly(
                    "messages", "messages/validation", "messages/errors", "messages/messages");
        }

        @Test
        @DisplayName("Should use default basenames when spring.messages.basename is empty")
        void emptyBasename() {
            when(environment.getProperty("spring.messages.basename")).thenReturn("");

            SimpliXMessageSourceAutoConfiguration config = new SimpliXMessageSourceAutoConfiguration();
            MessageSourceProperties props = config.messageSourceProperties(environment);

            assertThat(props.getBasename()).containsExactly(
                    "messages", "messages/validation", "messages/errors", "messages/messages");
        }
    }

    @Nested
    @DisplayName("messageSource")
    class MessageSourceTests {

        @Test
        @DisplayName("Should create hierarchical message source with library parent")
        void createHierarchicalMessageSource() {
            when(environment.getProperty("spring.messages.basename")).thenReturn(null);

            SimpliXMessageSourceAutoConfiguration config = new SimpliXMessageSourceAutoConfiguration();
            MessageSourceProperties props = config.messageSourceProperties(environment);
            MessageSource messageSource = config.messageSource(props);

            assertThat(messageSource).isNotNull();
        }

        @Test
        @DisplayName("Should create message source with cache duration when configured")
        void createWithCacheDuration() {
            when(environment.getProperty("spring.messages.basename")).thenReturn(null);

            SimpliXMessageSourceAutoConfiguration config = new SimpliXMessageSourceAutoConfiguration();
            MessageSourceProperties props = config.messageSourceProperties(environment);
            props.setCacheDuration(Duration.ofSeconds(300));

            MessageSource messageSource = config.messageSource(props);

            assertThat(messageSource).isNotNull();
        }
    }
}
