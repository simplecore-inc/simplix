package dev.simplecore.simplix.core.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EventContext")
class EventContextTest {

    @AfterEach
    void cleanup() {
        EventContext.clear();
    }

    @Nested
    @DisplayName("isEventsSuppressed")
    class IsEventsSuppressed {

        @Test
        @DisplayName("should return false by default")
        void shouldReturnFalseByDefault() {
            assertThat(EventContext.isEventsSuppressed()).isFalse();
        }
    }

    @Nested
    @DisplayName("suppressEvents with Runnable")
    class SuppressEventsRunnable {

        @Test
        @DisplayName("should suppress events during execution")
        void shouldSuppressDuringExecution() {
            AtomicBoolean wasSuppressed = new AtomicBoolean(false);

            EventContext.suppressEvents(() -> {
                wasSuppressed.set(EventContext.isEventsSuppressed());
            });

            assertThat(wasSuppressed.get()).isTrue();
            assertThat(EventContext.isEventsSuppressed()).isFalse();
        }

        @Test
        @DisplayName("should restore state after exception")
        void shouldRestoreStateAfterException() {
            assertThatThrownBy(() -> {
                EventContext.suppressEvents(() -> {
                    throw new RuntimeException("test error");
                });
            }).isInstanceOf(RuntimeException.class);

            assertThat(EventContext.isEventsSuppressed()).isFalse();
        }
    }

    @Nested
    @DisplayName("suppressEvents with Supplier")
    class SuppressEventsSupplier {

        @Test
        @DisplayName("should suppress events and return result")
        void shouldSuppressAndReturnResult() {
            String result = EventContext.suppressEvents(() -> {
                assertThat(EventContext.isEventsSuppressed()).isTrue();
                return "computed-value";
            });

            assertThat(result).isEqualTo("computed-value");
            assertThat(EventContext.isEventsSuppressed()).isFalse();
        }

        @Test
        @DisplayName("should restore state after supplier exception")
        void shouldRestoreStateAfterSupplierException() {
            assertThatThrownBy(() -> {
                EventContext.<String>suppressEvents(() -> {
                    throw new RuntimeException("supplier error");
                });
            }).isInstanceOf(RuntimeException.class);

            assertThat(EventContext.isEventsSuppressed()).isFalse();
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("should clear thread-local state")
        void shouldClearState() {
            EventContext.suppressEvents(() -> {
                // Deliberately do nothing - just sets the flag
            });
            EventContext.clear();

            assertThat(EventContext.isEventsSuppressed()).isFalse();
        }
    }
}
