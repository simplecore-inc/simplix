package dev.simplecore.simplix.excel.convert;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConverterRegistry")
class ConverterRegistryTest {

    @BeforeEach
    void setUp() {
        ConverterRegistry.clear();
    }

    @AfterEach
    void tearDown() {
        ConverterRegistry.clear();
    }

    @Nested
    @DisplayName("toString conversion")
    class ToStringTests {

        @Test
        @DisplayName("should return default value for null input")
        void shouldReturnDefaultForNull() {
            assertThat(ConverterRegistry.toString(null, "N/A")).isEqualTo("N/A");
        }

        @Test
        @DisplayName("should use registered converter")
        void shouldUseRegisteredConverter() {
            ConverterRegistry.registerToStringConverter(Integer.class, i -> "INT:" + i);

            assertThat(ConverterRegistry.toString(42, "default")).isEqualTo("INT:42");
        }

        @Test
        @DisplayName("should fall back to Object.toString when no converter found")
        void shouldFallBackToToString() {
            assertThat(ConverterRegistry.toString(42, "default")).isEqualTo("42");
        }

        @Test
        @DisplayName("should find supertype converter")
        void shouldFindSupertypeConverter() {
            ConverterRegistry.registerToStringConverter(Number.class, n -> "NUM:" + n);

            // Integer is a subtype of Number
            assertThat(ConverterRegistry.toString(42, "default")).isEqualTo("NUM:42");
        }

        @Test
        @DisplayName("should return default value when converter throws exception")
        void shouldReturnDefaultOnError() {
            ConverterRegistry.registerToStringConverter(Integer.class, i -> {
                throw new RuntimeException("Conversion error");
            });

            assertThat(ConverterRegistry.toString(42, "fallback")).isEqualTo("fallback");
        }
    }

    @Nested
    @DisplayName("fromString conversion")
    class FromStringTests {

        @Test
        @DisplayName("should return default value for null input")
        void shouldReturnDefaultForNull() {
            assertThat(ConverterRegistry.fromString(null, Integer.class, 0)).isEqualTo(0);
        }

        @Test
        @DisplayName("should use registered converter")
        void shouldUseRegisteredConverter() {
            ConverterRegistry.registerFromStringConverter(Integer.class, Integer::parseInt);

            assertThat(ConverterRegistry.fromString("42", Integer.class, 0)).isEqualTo(42);
        }

        @Test
        @DisplayName("should return String directly when target type is String")
        void shouldReturnStringDirectly() {
            assertThat(ConverterRegistry.fromString("hello", String.class, "default"))
                    .isEqualTo("hello");
        }

        @Test
        @DisplayName("should return default value when no converter found")
        void shouldReturnDefaultWhenNoConverter() {
            assertThat(ConverterRegistry.fromString("42", Integer.class, 0)).isEqualTo(0);
        }

        @Test
        @DisplayName("should return default value when converter throws exception")
        void shouldReturnDefaultOnError() {
            ConverterRegistry.registerFromStringConverter(Integer.class, s -> {
                throw new RuntimeException("Parse error");
            });

            assertThat(ConverterRegistry.fromString("invalid", Integer.class, -1)).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("clear")
    class ClearTests {

        @Test
        @DisplayName("should clear all registered converters")
        void shouldClearAll() {
            ConverterRegistry.registerToStringConverter(Integer.class, i -> "INT:" + i);
            ConverterRegistry.registerFromStringConverter(Integer.class, Integer::parseInt);

            ConverterRegistry.clear();

            // After clear, should use default toString
            assertThat(ConverterRegistry.toString(42, "default")).isEqualTo("42");
            // After clear, no converter found
            assertThat(ConverterRegistry.fromString("42", Integer.class, 0)).isEqualTo(0);
        }
    }
}
