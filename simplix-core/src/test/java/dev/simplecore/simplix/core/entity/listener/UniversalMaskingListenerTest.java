package dev.simplecore.simplix.core.entity.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("UniversalMaskingListener")
class UniversalMaskingListenerTest {

    private UniversalMaskingListener listener;

    @BeforeEach
    void setUp() {
        listener = new UniversalMaskingListener();
    }

    // Test entities with @MaskSensitive annotation
    static class EmailEntity {
        @MaskSensitive(type = MaskSensitive.MaskType.EMAIL)
        private String email;

        public EmailEntity(String email) {
            this.email = email;
        }

        public String getEmail() { return email; }
    }

    static class PhoneEntity {
        @MaskSensitive(type = MaskSensitive.MaskType.PHONE)
        private String phone;

        public PhoneEntity(String phone) {
            this.phone = phone;
        }

        public String getPhone() { return phone; }
    }

    static class FullMaskEntity {
        @MaskSensitive(type = MaskSensitive.MaskType.FULL)
        private String secret;

        public FullMaskEntity(String secret) {
            this.secret = secret;
        }

        public String getSecret() { return secret; }
    }

    static class PartialMaskEntity {
        @MaskSensitive(type = MaskSensitive.MaskType.PARTIAL, keepFirst = 2, keepLast = 3)
        private String data;

        public PartialMaskEntity(String data) {
            this.data = data;
        }

        public String getData() { return data; }
    }

    static class DisabledMaskEntity {
        @MaskSensitive(type = MaskSensitive.MaskType.FULL, enabled = false)
        private String data;

        public DisabledMaskEntity(String data) {
            this.data = data;
        }

        public String getData() { return data; }
    }

    static class MinLengthEntity {
        @MaskSensitive(type = MaskSensitive.MaskType.FULL, minLength = 10)
        private String data;

        public MinLengthEntity(String data) {
            this.data = data;
        }

        public String getData() { return data; }
    }

    static class NoAnnotationEntity {
        private String name;

        public NoAnnotationEntity(String name) {
            this.name = name;
        }

        public String getName() { return name; }
    }

    static class CreditCardEntity {
        @MaskSensitive(type = MaskSensitive.MaskType.CREDIT_CARD)
        private String cardNumber;

        public CreditCardEntity(String cardNumber) {
            this.cardNumber = cardNumber;
        }

        public String getCardNumber() { return cardNumber; }
    }

    static class NonStringFieldEntity {
        @MaskSensitive(type = MaskSensitive.MaskType.FULL)
        private Integer number;

        public NonStringFieldEntity(Integer number) {
            this.number = number;
        }

        public Integer getNumber() { return number; }
    }

    @Nested
    @DisplayName("maskSensitiveFields")
    class MaskSensitiveFields {

        @Test
        @DisplayName("should mask email field")
        void shouldMaskEmailField() {
            EmailEntity entity = new EmailEntity("user@example.com");

            listener.maskSensitiveFields(entity);

            assertThat(entity.getEmail()).isEqualTo("us***@example.com");
        }

        @Test
        @DisplayName("should mask phone field")
        void shouldMaskPhoneField() {
            PhoneEntity entity = new PhoneEntity("010-1234-5678");

            listener.maskSensitiveFields(entity);

            assertThat(entity.getPhone()).isEqualTo("010-****-****");
        }

        @Test
        @DisplayName("should fully mask field with FULL type")
        void shouldFullyMaskField() {
            FullMaskEntity entity = new FullMaskEntity("my-secret-data");

            listener.maskSensitiveFields(entity);

            assertThat(entity.getSecret()).doesNotContain("my-secret-data");
            assertThat(entity.getSecret()).contains("*");
        }

        @Test
        @DisplayName("should partially mask field with PARTIAL type")
        void shouldPartiallyMaskField() {
            PartialMaskEntity entity = new PartialMaskEntity("1234567890");

            listener.maskSensitiveFields(entity);

            String masked = entity.getData();
            assertThat(masked).startsWith("12");
            assertThat(masked).endsWith("890");
            assertThat(masked).contains("*");
        }

        @Test
        @DisplayName("should not mask when enabled is false")
        void shouldNotMaskWhenDisabled() {
            DisabledMaskEntity entity = new DisabledMaskEntity("sensitive-data");

            listener.maskSensitiveFields(entity);

            assertThat(entity.getData()).isEqualTo("sensitive-data");
        }

        @Test
        @DisplayName("should not mask when value is shorter than minLength")
        void shouldNotMaskWhenBelowMinLength() {
            MinLengthEntity entity = new MinLengthEntity("short");

            listener.maskSensitiveFields(entity);

            assertThat(entity.getData()).isEqualTo("short");
        }

        @Test
        @DisplayName("should mask when value meets minLength")
        void shouldMaskWhenMeetsMinLength() {
            MinLengthEntity entity = new MinLengthEntity("long-enough-data");

            listener.maskSensitiveFields(entity);

            assertThat(entity.getData()).contains("*");
        }

        @Test
        @DisplayName("should not modify entity without annotation")
        void shouldNotModifyWithoutAnnotation() {
            NoAnnotationEntity entity = new NoAnnotationEntity("normal-data");

            listener.maskSensitiveFields(entity);

            assertThat(entity.getName()).isEqualTo("normal-data");
        }

        @Test
        @DisplayName("should handle null entity gracefully")
        void shouldHandleNullEntity() {
            assertThatNoException().isThrownBy(() -> listener.maskSensitiveFields(null));
        }

        @Test
        @DisplayName("should skip non-String fields with annotation")
        void shouldSkipNonStringFields() {
            NonStringFieldEntity entity = new NonStringFieldEntity(12345);

            listener.maskSensitiveFields(entity);

            assertThat(entity.getNumber()).isEqualTo(12345);
        }

        @Test
        @DisplayName("should mask credit card number")
        void shouldMaskCreditCard() {
            CreditCardEntity entity = new CreditCardEntity("1234-5678-9012-3456");

            listener.maskSensitiveFields(entity);

            assertThat(entity.getCardNumber()).isEqualTo("****-****-****-3456");
        }
    }
}
