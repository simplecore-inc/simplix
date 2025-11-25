package dev.simplecore.simplix.core.security.hashing;

import dev.simplecore.simplix.core.entity.converter.HashingAttributeConverter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * General-purpose hashing utility for creating one-way hashes of sensitive data.
 *
 * <p><b>Purpose:</b> Provides fast, deterministic hashing for:
 * <ul>
 *   <li>Lookups and indexing (e.g., email hash index for quick user search)</li>
 *   <li>Deduplication checks (e.g., checking if sensitive data already exists)</li>
 *   <li>Non-security-critical hashing (e.g., cache keys, fingerprints)</li>
 * </ul>
 *
 * <p><b>⚠ IMPORTANT - Security Considerations:</b>
 * <ul>
 *   <li>This utility uses <b>unsalted</b> SHA hashing for performance</li>
 *   <li><b>DO NOT use for password storage</b> - use {@code HashingAttributeConverter} with PBKDF2 instead</li>
 *   <li><b>DO NOT use for authentication tokens</b> - use proper key derivation functions</li>
 *   <li>Suitable for <b>indexing and lookup</b> where speed is critical</li>
 * </ul>
 *
 * <p><b>When to Use Which:</b>
 * <table border="1">
 *   <tr>
 *     <th>Use Case</th>
 *     <th>Tool</th>
 *     <th>Reason</th>
 *   </tr>
 *   <tr>
 *     <td>Email/phone lookup index</td>
 *     <td>HashingUtils</td>
 *     <td>Fast, deterministic, no salt needed</td>
 *   </tr>
 *   <tr>
 *     <td>Password storage</td>
 *     <td>HashingAttributeConverter</td>
 *     <td>Slow (PBKDF2), salted, secure</td>
 *   </tr>
 *   <tr>
 *     <td>Cache keys</td>
 *     <td>HashingUtils</td>
 *     <td>Fast, simple</td>
 *   </tr>
 *   <tr>
 *     <td>Sensitive personal data</td>
 *     <td>HashingAttributeConverter</td>
 *     <td>Salted for additional security</td>
 *   </tr>
 * </table>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * // For email lookup index
 * String emailHash = HashingUtils.hash(email.toLowerCase().trim());
 * userRepository.findByEmailHash(emailHash);
 *
 * // For cache key
 * String cacheKey = HashingUtils.hash(userId + ":" + requestParams);
 * }</pre>
 *
 * <p><b>Implementation Notes:</b>
 * <ul>
 *   <li>Always trims input before hashing to ensure consistency</li>
 *   <li>Returns Base64-encoded hash for easier storage and comparison</li>
 *   <li>Default algorithm: SHA-256 (32 bytes → 44 Base64 characters)</li>
 *   <li>Thread-safe (all methods are static and stateless)</li>
 * </ul>
 *
 * @see HashingAttributeConverter
 */
@Slf4j
public final class HashingUtils {

    // Algorithm constants for consistent usage
    public static final String SHA_256 = "SHA-256";
    public static final String SHA_512 = "SHA-512";

    private static final String DEFAULT_ALGORITHM = SHA_256;

    private HashingUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a SHA-256 hash of the input string after trimming whitespace.
     * This is the primary method for general hashing.
     *
     * @param input The string to hash (will be trimmed)
     * @return Base64 encoded SHA-256 hash of the trimmed input
     */
    public static String hash(String input) {
        return hash(input, DEFAULT_ALGORITHM);
    }

    /**
     * Creates a hash using the specified algorithm after trimming whitespace.
     *
     * @param input The string to hash (will be trimmed)
     * @param algorithm The hash algorithm to use (e.g., "SHA-256", "SHA-512")
     * @return Base64 encoded hash of the trimmed input
     */
    public static String hash(String input, String algorithm) {
        if (input == null) {
            return null;
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(trimmed.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to create message digest for algorithm: {}", algorithm, e);
            throw new HashingException("Hashing failed: " + algorithm + " not available", e);
        }
    }

    /**
     * Validates if a string is a valid SHA-256 hash in Base64 format.
     *
     * @param hash The string to validate
     * @return true if the string appears to be a valid SHA-256 hash
     */
    public static boolean isValidSha256Hash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }

        // SHA-256 produces 32 bytes, which in Base64 is 44 characters (including padding)
        return hash.matches("^[A-Za-z0-9+/]+=*$") && hash.length() == 44;
    }

    /**
     * Validates if a string is a valid SHA-512 hash in Base64 format.
     *
     * @param hash The string to validate
     * @return true if the string appears to be a valid SHA-512 hash
     */
    public static boolean isValidSha512Hash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }

        // SHA-512 produces 64 bytes, which in Base64 is 88 characters (including padding)
        return hash.matches("^[A-Za-z0-9+/]+=*$") && hash.length() == 88;
    }

    /**
     * Validates if a string is a valid hash in Base64 format.
     * More lenient than specific hash validators, accepts various hash lengths.
     *
     * @param hash The string to validate
     * @return true if the string appears to be a valid base64 hash
     */
    public static boolean isValidHash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return false;
        }

        // Base64 pattern with reasonable length for hash outputs
        return hash.matches("^[A-Za-z0-9+/]+=*$") && hash.length() >= 20 && hash.length() <= 100;
    }

    /**
     * Validates if a string is a valid hash for the specified algorithm.
     *
     * @param hash The string to validate
     * @param algorithm The hash algorithm (e.g., "SHA-256", "SHA-512")
     * @return true if the string appears to be a valid hash for the algorithm
     */
    public static boolean isValidHashForAlgorithm(String hash, String algorithm) {
        if (hash == null || hash.isEmpty() || algorithm == null) {
            return false;
        }

        switch (algorithm.toUpperCase()) {
            case "SHA-256":
            case "SHA256":
                return isValidSha256Hash(hash);
            case "SHA-512":
            case "SHA512":
                return isValidSha512Hash(hash);
            default:
                // For unknown algorithms, use generic validation
                return isValidHash(hash);
        }
    }

    /**
     * Custom exception for hashing failures.
     */
    public static class HashingException extends RuntimeException {
        public HashingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}