package dev.simplecore.simplix.sync.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryStateStoreTest {

    private InMemoryStateStore<TestState> store;

    @BeforeEach
    void setUp() {
        store = new InMemoryStateStore<>(TestState::new);
    }

    @Nested
    @DisplayName("Basic operations")
    class BasicOperations {

        @Test
        @DisplayName("should create state lazily via getOrCreate")
        void shouldCreateLazily() {
            TestState state = store.getOrCreate("key-1");
            assertThat(state).isNotNull();
            assertThat(state.id).isEqualTo("key-1");
            assertThat(store.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return same instance on repeated getOrCreate")
        void shouldReturnSameInstance() {
            TestState first = store.getOrCreate("key-1");
            TestState second = store.getOrCreate("key-1");
            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("should return null for non-existent key via get")
        void shouldReturnNullForMissing() {
            assertThat(store.get("unknown")).isNull();
        }

        @Test
        @DisplayName("should return all states")
        void shouldReturnAllStates() {
            store.getOrCreate("key-1");
            store.getOrCreate("key-2");
            assertThat(store.getAll()).hasSize(2);
        }

        @Test
        @DisplayName("should return all keys")
        void shouldReturnAllKeys() {
            store.getOrCreate("key-1");
            store.getOrCreate("key-2");
            assertThat(store.getAllKeys()).containsExactlyInAnyOrder("key-1", "key-2");
        }

        @Test
        @DisplayName("should remove state by key")
        void shouldRemoveByKey() {
            store.getOrCreate("key-1");
            TestState removed = store.remove("key-1");
            assertThat(removed).isNotNull();
            assertThat(store.get("key-1")).isNull();
            assertThat(store.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return null when removing non-existent key")
        void shouldReturnNullWhenRemovingMissing() {
            assertThat(store.remove("unknown")).isNull();
        }
    }

    @Nested
    @DisplayName("Synchronized mutation")
    class SynchronizedMutation {

        @Test
        @DisplayName("should mutate state synchronously")
        void shouldMutateState() {
            store.getOrCreate("key-1");
            store.mutate("key-1", state -> state.value = 42);
            assertThat(store.get("key-1").value).isEqualTo(42);
        }

        @Test
        @DisplayName("should create state if not exists on mutate")
        void shouldCreateOnMutate() {
            store.mutate("key-1", state -> state.value = 10);
            assertThat(store.get("key-1")).isNotNull();
            assertThat(store.get("key-1").value).isEqualTo(10);
        }

        @Test
        @DisplayName("should compute and return result")
        void shouldComputeAndReturn() {
            store.getOrCreate("key-1");
            boolean result = store.compute("key-1", state -> {
                state.value = 99;
                return true;
            });
            assertThat(result).isTrue();
            assertThat(store.get("key-1").value).isEqualTo(99);
        }
    }

    static class TestState {
        final String id;
        int value = 0;

        TestState(String id) {
            this.id = id;
        }
    }
}
