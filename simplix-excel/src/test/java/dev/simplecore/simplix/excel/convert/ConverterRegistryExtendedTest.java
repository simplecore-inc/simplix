package dev.simplecore.simplix.excel.convert;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConverterRegistry - Extended Coverage")
class ConverterRegistryExtendedTest {

    @BeforeEach
    void setUp() {
        ConverterRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        ConverterRegistry.clear();
    }

    @Nested
    @DisplayName("toString - supertype converter error handling")
    class SupertypeErrorTests {

        @Test
        @DisplayName("should return default value when supertype converter throws exception")
        void shouldReturnDefaultOnSupertypeError() {
            // Register a converter for Number (supertype of Integer)
            ConverterRegistry.registerToStringConverter(Number.class, n -> {
                throw new RuntimeException("Supertype conversion error");
            });

            // Integer is subtype of Number - should find supertype converter, which throws
            String result = ConverterRegistry.toString(42, "fallback");
            assertThat(result).isEqualTo("fallback");
        }
    }

    @Nested
    @DisplayName("toString - exact match takes priority over supertype")
    class PriorityTests {

        @Test
        @DisplayName("should prefer exact type match over supertype")
        void shouldPreferExactMatch() {
            ConverterRegistry.registerToStringConverter(Number.class, n -> "NUMBER:" + n);
            ConverterRegistry.registerToStringConverter(Integer.class, i -> "INTEGER:" + i);

            // Exact match should be preferred
            assertThat(ConverterRegistry.toString(42, "default")).isEqualTo("INTEGER:42");
        }
    }

    @Nested
    @DisplayName("fromString - various edge cases")
    class FromStringEdgeCaseTests {

        @Test
        @DisplayName("should handle converter returning null")
        void shouldHandleConverterReturningNull() {
            ConverterRegistry.registerFromStringConverter(Integer.class, s -> null);

            // Converter returns null, which is a valid result
            Integer result = ConverterRegistry.fromString("anything", Integer.class, -1);
            assertThat(result).isNull();
        }
    }
}
