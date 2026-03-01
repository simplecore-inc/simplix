package dev.simplecore.simplix.messaging.broker.redis;

/**
 * Encoding strategy for binary payload storage in Redis Streams.
 *
 * <p>Determines how the binary {@code byte[]} payload is stored as a Redis Stream field value:
 * <ul>
 *   <li>{@link #BASE64} - encodes payload as a Base64 string (safe with StringRedisTemplate)</li>
 *   <li>{@link #RAW} - stores raw binary bytes directly (enables protobuf viewers in Redis clients)</li>
 * </ul>
 */
public enum PayloadEncoding {

    /**
     * Default. Encodes binary payload as Base64 string.
     *
     * <p>Pros: compatible with StringRedisTemplate, all fields are human-readable strings.
     * <p>Cons: ~33% payload size overhead, Redis client protobuf viewers cannot decode directly.
     */
    BASE64,

    /**
     * Stores binary payload as raw bytes in Redis.
     *
     * <p>Pros: no size overhead, Redis client protobuf viewers can decode the payload field directly.
     * <p>Cons: payload field appears as binary blob in redis-cli.
     */
    RAW
}
