package dev.simplecore.simplix.email.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailAddress")
class EmailAddressTest {

    @Nested
    @DisplayName("of(address)")
    class OfAddress {

        @Test
        @DisplayName("Should create EmailAddress with address only")
        void shouldCreateWithAddressOnly() {
            EmailAddress emailAddress = EmailAddress.of("user@example.com");

            assertThat(emailAddress.getAddress()).isEqualTo("user@example.com");
            assertThat(emailAddress.getName()).isNull();
        }
    }

    @Nested
    @DisplayName("of(name, address)")
    class OfNameAndAddress {

        @Test
        @DisplayName("Should create EmailAddress with name and address")
        void shouldCreateWithNameAndAddress() {
            EmailAddress emailAddress = EmailAddress.of("John Doe", "john@example.com");

            assertThat(emailAddress.getAddress()).isEqualTo("john@example.com");
            assertThat(emailAddress.getName()).isEqualTo("John Doe");
        }
    }

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("Should create EmailAddress using builder")
        void shouldCreateUsingBuilder() {
            EmailAddress emailAddress = EmailAddress.builder()
                    .address("test@example.com")
                    .name("Test User")
                    .build();

            assertThat(emailAddress.getAddress()).isEqualTo("test@example.com");
            assertThat(emailAddress.getName()).isEqualTo("Test User");
        }
    }

    @Nested
    @DisplayName("toFormattedString")
    class ToFormattedString {

        @Test
        @DisplayName("Should return address only when name is null")
        void shouldReturnAddressOnlyWhenNameIsNull() {
            EmailAddress emailAddress = EmailAddress.of("user@example.com");

            assertThat(emailAddress.toFormattedString()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("Should return address only when name is blank")
        void shouldReturnAddressOnlyWhenNameIsBlank() {
            EmailAddress emailAddress = EmailAddress.of("  ", "user@example.com");

            assertThat(emailAddress.toFormattedString()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("Should return formatted string with name and address")
        void shouldReturnFormattedStringWithNameAndAddress() {
            EmailAddress emailAddress = EmailAddress.of("John Doe", "john@example.com");

            assertThat(emailAddress.toFormattedString()).isEqualTo("\"John Doe\" <john@example.com>");
        }
    }

    @Nested
    @DisplayName("toMaskedString")
    class ToMaskedString {

        @Test
        @DisplayName("Should mask email address preserving first character and domain")
        void shouldMaskEmailAddress() {
            EmailAddress emailAddress = EmailAddress.of("john.doe@example.com");

            assertThat(emailAddress.toMaskedString()).isEqualTo("j***@example.com");
        }

        @Test
        @DisplayName("Should return *** when address is null")
        void shouldReturnMaskWhenAddressIsNull() {
            EmailAddress emailAddress = EmailAddress.builder().build();

            assertThat(emailAddress.toMaskedString()).isEqualTo("***");
        }

        @Test
        @DisplayName("Should return *** when address has no @ symbol")
        void shouldReturnMaskWhenNoAtSymbol() {
            EmailAddress emailAddress = EmailAddress.builder().address("invalid").build();

            assertThat(emailAddress.toMaskedString()).isEqualTo("***");
        }

        @Test
        @DisplayName("Should return *** when local part is empty")
        void shouldReturnMaskWhenLocalPartEmpty() {
            EmailAddress emailAddress = EmailAddress.builder().address("@example.com").build();

            assertThat(emailAddress.toMaskedString()).isEqualTo("***");
        }

        @Test
        @DisplayName("Should mask single character local part")
        void shouldMaskSingleCharacterLocalPart() {
            EmailAddress emailAddress = EmailAddress.of("a@example.com");

            assertThat(emailAddress.toMaskedString()).isEqualTo("a***@example.com");
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("Should be equal when address and name are the same")
        void shouldBeEqualWhenSame() {
            EmailAddress a = EmailAddress.of("John", "john@example.com");
            EmailAddress b = EmailAddress.of("John", "john@example.com");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when address differs")
        void shouldNotBeEqualWhenAddressDiffers() {
            EmailAddress a = EmailAddress.of("john@example.com");
            EmailAddress b = EmailAddress.of("jane@example.com");

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("Should contain address in toString output")
        void shouldContainAddress() {
            EmailAddress emailAddress = EmailAddress.of("user@example.com");

            assertThat(emailAddress.toString()).contains("user@example.com");
        }
    }
}
