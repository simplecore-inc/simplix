package dev.simplecore.simplix.core.convert.bool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StandardBooleanConverter")
class StandardBooleanConverterTest {

    private StandardBooleanConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StandardBooleanConverter();
    }

    @Nested
    @DisplayName("fromString")
    class FromString {

        @ParameterizedTest
        @ValueSource(strings = {"true", "1", "yes", "y", "on"})
        @DisplayName("should convert truthy values to true")
        void shouldConvertTruthyValues(String value) {
            assertThat(converter.fromString(value)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"TRUE", "True", "YES", "Yes", "ON", "On", "Y"})
        @DisplayName("should handle case insensitive truthy values")
        void shouldHandleCaseInsensitiveTruthy(String value) {
            assertThat(converter.fromString(value)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"false", "0", "no", "n", "off"})
        @DisplayName("should convert falsy values to false")
        void shouldConvertFalsyValues(String value) {
            assertThat(converter.fromString(value)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"FALSE", "False", "NO", "No", "OFF", "Off", "N"})
        @DisplayName("should handle case insensitive falsy values")
        void shouldHandleCaseInsensitiveFalsy(String value) {
            assertThat(converter.fromString(value)).isFalse();
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(converter.fromString(null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty string")
        void shouldReturnNullForEmpty() {
            assertThat(converter.fromString("")).isNull();
        }

        @Test
        @DisplayName("should return null for whitespace-only string")
        void shouldReturnNullForWhitespace() {
            assertThat(converter.fromString("   ")).isNull();
        }

        @Test
        @DisplayName("should handle values with leading/trailing whitespace")
        void shouldHandleWhitespace() {
            assertThat(converter.fromString("  true  ")).isTrue();
            assertThat(converter.fromString("  false  ")).isFalse();
        }

        @Test
        @DisplayName("should throw for invalid value")
        void shouldThrowForInvalidValue() {
            assertThatThrownBy(() -> converter.fromString("maybe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid boolean value");
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should convert true to 'true'")
        void shouldConvertTrueToString() {
            assertThat(converter.toString(true)).isEqualTo("true");
        }

        @Test
        @DisplayName("should convert false to 'false'")
        void shouldConvertFalseToString() {
            assertThat(converter.toString(false)).isEqualTo("false");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(converter.toString(null)).isNull();
        }
    }
}
