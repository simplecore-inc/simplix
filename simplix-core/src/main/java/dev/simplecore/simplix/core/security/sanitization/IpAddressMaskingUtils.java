package dev.simplecore.simplix.core.security.sanitization;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * IP Address Masking Utilities
 * <p>
 * Provides subnet-level masking for IP addresses to comply with:
 * - GDPR Art. 32: Pseudonymization of personal data
 * - ISO 27001 A.12.4.1: Event logging with appropriate protection
 * <p>
 * Masking Strategy (Option 1 - Subnet Level):
 * - IPv4: Masks last octet (e.g., 192.168.1.123 → 192.168.1.0)
 * - IPv6: Masks last segment (e.g., 2001:db8:85a3::8a2e:370:7334 → 2001:db8:85a3::8a2e:370:0000)
 * <p>
 * Benefits:
 * - Allows network range analysis (attack patterns, geographical analysis)
 * - Satisfies GDPR pseudonymization requirements
 * - Enables partial identification for operational needs
 * - Avoids excessive one-way hashing
 */
@UtilityClass
@Slf4j
public class IpAddressMaskingUtils {

    /**
     * Mask IP address at subnet level
     * <p>
     * IPv4: Replaces last octet with 0 (e.g., 192.168.1.123 → 192.168.1.0)
     * IPv6: Replaces last segment with 0000 (e.g., 2001:db8::7334 → 2001:db8::0)
     * <p>
     * Contract:
     * - Returns null if ipAddress is null
     * - Returns original string if format is invalid (logs warning)
     * - Handles both full and abbreviated IPv6 notation
     *
     * @param ipAddress IP address to mask (IPv4 or IPv6)
     * @return Masked IP address at subnet level
     */
    public String maskSubnetLevel(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }

        // Trim whitespace
        ipAddress = ipAddress.trim();

        if (ipAddress.isEmpty()) {
            return ipAddress;
        }

        // IPv4 handling
        if (ipAddress.contains(".")) {
            return maskIpv4(ipAddress);
        }

        // IPv6 handling
        if (ipAddress.contains(":")) {
            return maskIpv6(ipAddress);
        }

        // Unknown format - return as-is with warning
        log.warn("⚠ Unknown IP address format, returning unmasked: {}", ipAddress);
        return ipAddress;
    }

    /**
     * Mask IPv4 address by replacing last octet with 0
     * <p>
     * Example: 192.168.1.123 → 192.168.1.0
     *
     * @param ipv4 IPv4 address
     * @return Masked IPv4 address
     */
    private String maskIpv4(String ipv4) {
        String[] parts = ipv4.split("\\.");

        // Validate IPv4 format (should have exactly 4 parts)
        if (parts.length != 4) {
            log.warn("⚠ Invalid IPv4 format, returning unmasked: {}", ipv4);
            return ipv4;
        }

        // Replace last octet with 0
        return parts[0] + "." + parts[1] + "." + parts[2] + ".0";
    }

    /**
     * Mask IPv6 address by replacing last segment with 0000
     * <p>
     * Examples:
     * - Full notation: 2001:0db8:85a3:0000:0000:8a2e:0370:7334 → 2001:0db8:85a3:0000:0000:8a2e:0370:0000
     * - Abbreviated: 2001:db8:85a3::8a2e:370:7334 → 2001:db8:85a3::8a2e:370:0
     * - Compressed: 2001:db8::7334 → 2001:db8::0
     *
     * @param ipv6 IPv6 address
     * @return Masked IPv6 address
     */
    private String maskIpv6(String ipv6) {
        // Find last colon separator
        int lastColonIndex = ipv6.lastIndexOf(":");

        if (lastColonIndex == -1) {
            log.warn("⚠ Invalid IPv6 format (no colon found), returning unmasked: {}", ipv6);
            return ipv6;
        }

        // Extract prefix and last segment
        String prefix = ipv6.substring(0, lastColonIndex + 1);

        // Replace last segment with 0 (or 0000 for full notation)
        // Use simple "0" which is valid for both full and abbreviated notation
        return prefix + "0";
    }

    /**
     * Check if an IP address is already masked
     * <p>
     * Detects if the IP address ends with ".0" (IPv4) or ":0" or ":0000" (IPv6)
     * <p>
     * Note: This is a simple heuristic and may have false positives
     * (e.g., legitimate IPs ending in .0 like 10.0.0.0)
     *
     * @param ipAddress IP address to check
     * @return true if appears to be masked, false otherwise
     */
    public boolean isMasked(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return false;
        }

        ipAddress = ipAddress.trim();

        // IPv4: Check if ends with .0
        if (ipAddress.contains(".")) {
            return ipAddress.endsWith(".0");
        }

        // IPv6: Check if ends with :0 or :0000
        if (ipAddress.contains(":")) {
            return ipAddress.endsWith(":0") || ipAddress.endsWith(":0000");
        }

        return false;
    }

    /**
     * Validate IPv4 and IPv6 address format
     * <p>
     * Supports:
     * - IPv4: Standard dotted-decimal notation (e.g., 192.168.1.1)
     * - IPv6: Full and abbreviated notation (e.g., 2001:db8::1)
     *
     * @param ip IP address to validate
     * @return true if valid IPv4 or IPv6 format, false otherwise
     */
    public boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // IPv4 pattern
        String ipv4Pattern =
                "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";

        // IPv6 pattern (simplified format, includes full and abbreviated forms)
        String ipv6Pattern =
                "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|" +
                        "([0-9a-fA-F]{1,4}:){1,7}:|" +
                        "([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +
                        "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|" +
                        "([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +
                        "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|" +
                        "([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +
                        "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|" +
                        ":((:[0-9a-fA-F]{1,4}){1,7}|:)|" +
                        "fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|" +
                        "::(ffff(:0{1,4}){0,1}:){0,1}" +
                        "((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}" +
                        "(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|" +
                        "([0-9a-fA-F]{1,4}:){1,4}:" +
                        "((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}" +
                        "(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$";

        return ip.matches(ipv4Pattern) || ip.matches(ipv6Pattern);
    }
}
