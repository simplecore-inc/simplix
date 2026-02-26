package dev.simplecore.simplix.stream.core.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Unique identifier for a subscription.
 * <p>
 * Composed of resource name and parameter hash.
 * Interval is NOT part of the key - same resource + params = same key.
 */
@Getter
@EqualsAndHashCode
@ToString
public class SubscriptionKey {

    private final String resource;
    private final String paramsHash;
    private final Map<String, Object> params;
    private final String keyString;

    private SubscriptionKey(String resource, Map<String, Object> params, String paramsHash) {
        this.resource = resource;
        this.params = Collections.unmodifiableMap(new TreeMap<>(params));
        this.paramsHash = paramsHash;
        this.keyString = resource + ":" + paramsHash;
    }

    /**
     * Create a subscription key from resource and parameters.
     *
     * @param resource the resource name
     * @param params   the subscription parameters
     * @return the subscription key
     */
    public static SubscriptionKey of(String resource, Map<String, Object> params) {
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("Resource name cannot be null or blank");
        }

        Map<String, Object> safeParams = params != null ? params : Collections.emptyMap();
        String hash = computeHash(safeParams);
        return new SubscriptionKey(resource, safeParams, hash);
    }

    /**
     * Create a subscription key from a key string (resource:hash format).
     *
     * @param keyString the key string
     * @return the subscription key (without params - for lookup only)
     */
    public static SubscriptionKey fromString(String keyString) {
        if (keyString == null || !keyString.contains(":")) {
            throw new IllegalArgumentException("Invalid key string format: " + keyString);
        }

        int colonIndex = keyString.indexOf(':');
        String resource = keyString.substring(0, colonIndex);
        String hash = keyString.substring(colonIndex + 1);

        return new SubscriptionKey(resource, Collections.emptyMap(), hash);
    }

    /**
     * Get the key as a string (resource:hash format).
     *
     * @return the key string
     */
    public String toKeyString() {
        return keyString;
    }

    private static String computeHash(Map<String, Object> params) {
        if (params.isEmpty()) {
            return "empty";
        }

        try {
            // Sort params for consistent hashing
            TreeMap<String, Object> sorted = new TreeMap<>(params);
            StringBuilder sb = new StringBuilder();
            sorted.forEach((k, v) -> sb.append(k).append("=").append(v).append(";"));

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));

            // Convert to hex string (first 8 characters for brevity)
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(4, digest.length); i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash code
            return String.format("%08x", params.hashCode());
        }
    }
}
