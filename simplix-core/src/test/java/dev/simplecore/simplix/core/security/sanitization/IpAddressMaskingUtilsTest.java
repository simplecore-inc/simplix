package dev.simplecore.simplix.core.security.sanitization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IpAddressMaskingUtils")
class IpAddressMaskingUtilsTest {

    @Nested
    @DisplayName("maskSubnetLevel")
    class MaskSubnetLevel {

        @Test
        @DisplayName("should mask IPv4 last octet with 0")
        void shouldMaskIpv4LastOctet() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("192.168.1.123"))
                .isEqualTo("192.168.1.0");
        }

        @Test
        @DisplayName("should mask different IPv4 addresses")
        void shouldMaskDifferentIpv4() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("10.0.0.5"))
                .isEqualTo("10.0.0.0");
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("172.16.254.1"))
                .isEqualTo("172.16.254.0");
        }

        @Test
        @DisplayName("should mask IPv6 last segment with 0")
        void shouldMaskIpv6LastSegment() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("2001:db8:85a3::8a2e:370:7334"))
                .isEqualTo("2001:db8:85a3::8a2e:370:0");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNullForNull() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel(null)).isNull();
        }

        @Test
        @DisplayName("should return empty string for empty input")
        void shouldReturnEmptyForEmpty() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("")).isEmpty();
        }

        @Test
        @DisplayName("should handle whitespace-padded input")
        void shouldHandleWhitespacePadded() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("  192.168.1.100  "))
                .isEqualTo("192.168.1.0");
        }

        @Test
        @DisplayName("should return unknown format as-is")
        void shouldReturnUnknownFormatAsIs() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("not-an-ip"))
                .isEqualTo("not-an-ip");
        }

        @Test
        @DisplayName("should return invalid IPv4 format as-is")
        void shouldReturnInvalidIpv4AsIs() {
            assertThat(IpAddressMaskingUtils.maskSubnetLevel("192.168.1"))
                .isEqualTo("192.168.1");
        }
    }

    @Nested
    @DisplayName("isMasked")
    class IsMasked {

        @Test
        @DisplayName("should detect masked IPv4 ending with .0")
        void shouldDetectMaskedIpv4() {
            assertThat(IpAddressMaskingUtils.isMasked("192.168.1.0")).isTrue();
        }

        @Test
        @DisplayName("should detect unmasked IPv4")
        void shouldDetectUnmaskedIpv4() {
            assertThat(IpAddressMaskingUtils.isMasked("192.168.1.123")).isFalse();
        }

        @Test
        @DisplayName("should detect masked IPv6 ending with :0")
        void shouldDetectMaskedIpv6() {
            assertThat(IpAddressMaskingUtils.isMasked("2001:db8::0")).isTrue();
        }

        @Test
        @DisplayName("should detect masked IPv6 ending with :0000")
        void shouldDetectMaskedIpv6Full() {
            assertThat(IpAddressMaskingUtils.isMasked("2001:db8:85a3:0000:0000:8a2e:0370:0000")).isTrue();
        }

        @Test
        @DisplayName("should return false for null or empty")
        void shouldReturnFalseForNullOrEmpty() {
            assertThat(IpAddressMaskingUtils.isMasked(null)).isFalse();
            assertThat(IpAddressMaskingUtils.isMasked("")).isFalse();
        }
    }

    @Nested
    @DisplayName("isValidIpAddress")
    class IsValidIpAddress {

        @Test
        @DisplayName("should validate correct IPv4 addresses")
        void shouldValidateCorrectIpv4() {
            assertThat(IpAddressMaskingUtils.isValidIpAddress("192.168.1.1")).isTrue();
            assertThat(IpAddressMaskingUtils.isValidIpAddress("0.0.0.0")).isTrue();
            assertThat(IpAddressMaskingUtils.isValidIpAddress("255.255.255.255")).isTrue();
        }

        @Test
        @DisplayName("should reject invalid IPv4 addresses")
        void shouldRejectInvalidIpv4() {
            assertThat(IpAddressMaskingUtils.isValidIpAddress("256.168.1.1")).isFalse();
            assertThat(IpAddressMaskingUtils.isValidIpAddress("192.168.1")).isFalse();
        }

        @Test
        @DisplayName("should validate correct IPv6 addresses")
        void shouldValidateCorrectIpv6() {
            assertThat(IpAddressMaskingUtils.isValidIpAddress("2001:0db8:85a3:0000:0000:8a2e:0370:7334")).isTrue();
            assertThat(IpAddressMaskingUtils.isValidIpAddress("::1")).isTrue();
        }

        @Test
        @DisplayName("should return false for null or empty")
        void shouldReturnFalseForNullOrEmpty() {
            assertThat(IpAddressMaskingUtils.isValidIpAddress(null)).isFalse();
            assertThat(IpAddressMaskingUtils.isValidIpAddress("")).isFalse();
        }
    }
}
