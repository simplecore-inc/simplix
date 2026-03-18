package dev.simplecore.simplix.core.convert.enumeration;

import dev.simplecore.simplix.core.enums.SimpliXLabeledEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StandardEnumConverter - Extended Coverage")
class StandardEnumConverterExtendedTest {

    private StandardEnumConverter converter;

    enum Status {
        ACTIVE,
        INACTIVE,
        PENDING
    }

    enum WithGetter implements SimpliXLabeledEnum {
        VALUE_ONE("Label One"),
        VALUE_TWO("Label Two");

        private final String label;

        WithGetter(String label) {
            this.label = label;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }

    @BeforeEach
    void setUp() {
        converter = new StandardEnumConverter();
    }

    @Nested
    @DisplayName("fromString - case insensitive")
    class FromStringCaseInsensitive {

        @Test
        @DisplayName("should match case-insensitively")
        void shouldMatchCaseInsensitively() {
            Status result = converter.fromString("active", Status.class);
            assertThat(result).isEqualTo(Status.ACTIVE);
        }

        @Test
        @DisplayName("should match mixed case")
        void shouldMatchMixedCase() {
            Status result = converter.fromString("Active", Status.class);
            assertThat(result).isEqualTo(Status.ACTIVE);
        }

        @Test
        @DisplayName("should trim whitespace")
        void shouldTrimWhitespace() {
            Status result = converter.fromString("  PENDING  ", Status.class);
            assertThat(result).isEqualTo(Status.PENDING);
        }

        @Test
        @DisplayName("should throw for completely unknown value")
        void shouldThrowForUnknown() {
            assertThatThrownBy(() -> converter.fromString("UNKNOWN", Status.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot find value");
        }
    }

    @Nested
    @DisplayName("toMap with getters")
    class ToMapWithGetters {

        @Test
        @DisplayName("should include getter values from interface")
        void shouldIncludeGetterValues() {
            Map<String, Object> result = converter.toMap(WithGetter.VALUE_ONE);

            assertThat(result).containsEntry("type", "WithGetter");
            assertThat(result).containsEntry("value", "VALUE_ONE");
            assertThat(result).containsEntry("label", "Label One");
        }

        @Test
        @DisplayName("should return null for null enum")
        void shouldReturnNullForNull() {
            assertThat(converter.toMap(null)).isNull();
        }
    }

    @Nested
    @DisplayName("EnumConverter interface")
    class EnumConverterInterface {

        @Test
        @DisplayName("should get default converter via interface")
        void shouldGetDefaultConverter() {
            EnumConverter defaultConverter = EnumConverter.getDefault();
            assertThat(defaultConverter).isNotNull();
            assertThat(defaultConverter).isInstanceOf(StandardEnumConverter.class);
        }
    }
}
