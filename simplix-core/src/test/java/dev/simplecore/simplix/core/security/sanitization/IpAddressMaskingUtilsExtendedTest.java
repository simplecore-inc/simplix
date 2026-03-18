package dev.simplecore.simplix.core.security.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IpAddressMaskingUtils - Extended Coverage")
class IpAddressMaskingUtilsExtendedTest {

    @Nested
    @DisplayName("maskSubnetLevel")
    class MaskSubnetLevel {

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmpty() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("")).isEmpty();
        }

        @Test
        @DisplayName("should handle whitespace-only")
        void shouldHandleWhitespace() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("   ")).isEmpty();
        }

        @Test
        @DisplayName("should return unknown format as-is")
        void shouldReturnUnknownAsIs() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("not-an-ip")).isEqualTo("not-an-ip");
        }

        @Test
        @DisplayName("should handle invalid IPv4 format")
        void shouldHandleInvalidIpv4() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("192.168")).isEqualTo("192.168");
        }
    }

    @Nested
    @DisplayName("isMasked")
    class IsMasked {

        @Test
        @DisplayName("should detect masked IPv6")
        void shouldDetectMaskedIpv6() {
            assertThat(IpAddressMaskingUtils.isMasked("2001:db8::0")).isTrue();
            assertThat(IpAddressMaskingUtils.isMasked("2001:db8::0000")).isTrue();
        }

        @Test
        @DisplayName("should return false for non-IP")
        void shouldReturnFalseForNonIp() {
            assertThat(IpAddressMaskingUtils.isMasked("not-an-ip")).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidIpAddress")
    class IsValidIpAddress {

        @Test
        @DisplayName("should return false for null/empty")
        void shouldReturnFalseForNullEmpty() {
            assertThat(IpAddressMaskingUtils.isValidIpAddress(null)).isFalse();
            assertThat(IpAddressMaskingUtils.isValidIpAddress("")).isFalse();
        }

        @Test
        @DisplayName("should validate IPv4")
        void shouldValidateIpv4() {
            assertThat(IpAddressMaskingUtils.isValidIpAddress("192.168.1.1")).isTrue();
            assertThat(IpAddressMaskingUtils.isValidIpAddress("255.255.255.255")).isTrue();
            assertThat(IpAddressMaskingUtils.isValidIpAddress("999.999.999.999")).isFalse();
        }
    }
}
