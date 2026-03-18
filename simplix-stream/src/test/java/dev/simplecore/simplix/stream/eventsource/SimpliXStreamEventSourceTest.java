package dev.simplecore.simplix.stream.eventsource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SimpliXStreamEventSource default methods.
 */
@DisplayName("SimpliXStreamEventSource")
class SimpliXStreamEventSourceTest {

    private SimpliXStreamEventSource source;

    @BeforeEach
    void setUp() {
        source = new SimpliXStreamEventSource() {
            @Override
            public String getResource() {
                return "test-resource";
            }

            @Override
            public String getEventType() {
                return "TestEvent";
            }

            @Override
            public Map<String, Object> extractParams(Object payload) {
                return Map.of();
            }

            @Override
            public Object extractData(Object payload) {
                return payload;
            }
        };
    }

    @Nested
    @DisplayName("validateParams()")
    class ValidateParams {

        @Test
        @DisplayName("should return true by default")
        void shouldReturnTrueByDefault() {
            boolean result = source.validateParams(Map.of("key", "value"));

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true for empty params by default")
        void shouldReturnTrueForEmptyParams() {
            boolean result = source.validateParams(Map.of());

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getRequiredPermission()")
    class GetRequiredPermission {

        @Test
        @DisplayName("should return null by default")
        void shouldReturnNullByDefault() {
            String permission = source.getRequiredPermission();

            assertThat(permission).isNull();
        }
    }

    @Nested
    @DisplayName("supports()")
    class Supports {

        @Test
        @DisplayName("should return true when event type matches")
        void shouldReturnTrueWhenEventTypeMatches() {
            boolean result = source.supports("TestEvent", "payload");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when event type does not match")
        void shouldReturnFalseWhenEventTypeDoesNotMatch() {
            boolean result = source.supports("DifferentEvent", "payload");

            assertThat(result).isFalse();
        }
    }
}
