package dev.simplecore.simplix.core.entity.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UniversalMaskingListener - Extended Coverage")
class UniversalMaskingListenerExtendedTest {

    private UniversalMaskingListener listener;

    @BeforeEach
    void setUp() {
        listener = new UniversalMaskingListener();
    }

    static class TestEntity {
        @MaskSensitive(type = MaskSensitive.MaskType.FULL)
        private String fullMasked;

        @MaskSensitive(type = MaskSensitive.MaskType.PARTIAL, keepFirst = 2, keepLast = 2)
        private String partialMasked;

        @MaskSensitive(type = MaskSensitive.MaskType.EMAIL)
        private String email;

        @MaskSensitive(type = MaskSensitive.MaskType.PHONE)
        private String phone;

        @MaskSensitive(type = MaskSensitive.MaskType.CREDIT_CARD)
        private String creditCard;

        @MaskSensitive(type = MaskSensitive.MaskType.PAYMENT_TOKEN)
        private String paymentToken;

        @MaskSensitive(type = MaskSensitive.MaskType.IP_ADDRESS)
        private String ipAddress;

        @MaskSensitive(type = MaskSensitive.MaskType.JSON)
        private String jsonData;

        @MaskSensitive(type = MaskSensitive.MaskType.NONE)
        private String noMask;

        @MaskSensitive(type = MaskSensitive.MaskType.FULL, enabled = false)
        private String disabled;

        @MaskSensitive(type = MaskSensitive.MaskType.FULL, minLength = 10)
        private String minLengthField;

        private Integer nonStringField;
    }

    static class ChildEntity extends TestEntity {
        @MaskSensitive(type = MaskSensitive.MaskType.EMAIL)
        private String childEmail;
    }

    @Nested
    @DisplayName("maskSensitiveFields")
    class MaskSensitiveFields {

        @Test
        @DisplayName("should handle null entity")
        void shouldHandleNull() {
            listener.maskSensitiveFields(null);
            // No exception thrown
        }

        @Test
        @DisplayName("should mask FULL type")
        void shouldMaskFull() throws Exception {
            TestEntity entity = new TestEntity();
            entity.fullMasked = "secret-data";

            listener.maskSensitiveFields(entity);

            assertThat(entity.fullMasked).doesNotContain("secret");
        }

        @Test
        @DisplayName("should mask PARTIAL type")
        void shouldMaskPartial() throws Exception {
            TestEntity entity = new TestEntity();
            entity.partialMasked = "ABCDEFGHIJ";

            listener.maskSensitiveFields(entity);

            assertThat(entity.partialMasked).startsWith("AB");
            assertThat(entity.partialMasked).endsWith("IJ");
        }

        @Test
        @DisplayName("should mask EMAIL type")
        void shouldMaskEmail() throws Exception {
            TestEntity entity = new TestEntity();
            entity.email = "john@example.com";

            listener.maskSensitiveFields(entity);

            assertThat(entity.email).contains("@example.com");
            assertThat(entity.email).doesNotStartWith("john");
        }

        @Test
        @DisplayName("should mask PHONE type")
        void shouldMaskPhone() throws Exception {
            TestEntity entity = new TestEntity();
            entity.phone = "01012345678";

            listener.maskSensitiveFields(entity);

            assertThat(entity.phone).contains("010");
        }

        @Test
        @DisplayName("should mask CREDIT_CARD type")
        void shouldMaskCreditCard() throws Exception {
            TestEntity entity = new TestEntity();
            entity.creditCard = "1234-5678-9012-3456";

            listener.maskSensitiveFields(entity);

            assertThat(entity.creditCard).contains("3456");
        }

        @Test
        @DisplayName("should mask PAYMENT_TOKEN type")
        void shouldMaskPaymentToken() throws Exception {
            TestEntity entity = new TestEntity();
            entity.paymentToken = "pm_1234567890abcdef";

            listener.maskSensitiveFields(entity);

            assertThat(entity.paymentToken).startsWith("pm_");
        }

        @Test
        @DisplayName("should mask IP_ADDRESS type")
        void shouldMaskIpAddress() throws Exception {
            TestEntity entity = new TestEntity();
            entity.ipAddress = "192.168.1.100";

            listener.maskSensitiveFields(entity);

            assertThat(entity.ipAddress).isEqualTo("192.168.1.0");
        }

        @Test
        @DisplayName("should mask JSON type")
        void shouldMaskJson() throws Exception {
            TestEntity entity = new TestEntity();
            entity.jsonData = "password: secret123";

            listener.maskSensitiveFields(entity);

            assertThat(entity.jsonData).doesNotContain("secret123");
        }

        @Test
        @DisplayName("should not mask NONE type")
        void shouldNotMaskNone() throws Exception {
            TestEntity entity = new TestEntity();
            entity.noMask = "visible-data";

            listener.maskSensitiveFields(entity);

            assertThat(entity.noMask).isEqualTo("visible-data");
        }

        @Test
        @DisplayName("should not mask disabled field")
        void shouldNotMaskDisabled() throws Exception {
            TestEntity entity = new TestEntity();
            entity.disabled = "still-visible";

            listener.maskSensitiveFields(entity);

            assertThat(entity.disabled).isEqualTo("still-visible");
        }

        @Test
        @DisplayName("should respect minLength threshold")
        void shouldRespectMinLength() throws Exception {
            TestEntity entity = new TestEntity();
            entity.minLengthField = "short";

            listener.maskSensitiveFields(entity);

            assertThat(entity.minLengthField).isEqualTo("short");
        }

        @Test
        @DisplayName("should skip non-string fields")
        void shouldSkipNonString() throws Exception {
            TestEntity entity = new TestEntity();
            entity.nonStringField = 42;

            listener.maskSensitiveFields(entity);

            assertThat(entity.nonStringField).isEqualTo(42);
        }

        @Test
        @DisplayName("should process inherited fields")
        void shouldProcessInherited() throws Exception {
            ChildEntity entity = new ChildEntity();
            entity.childEmail = "child@example.com";

            listener.maskSensitiveFields(entity);

            assertThat(entity.childEmail).contains("@example.com");
        }
    }
}
