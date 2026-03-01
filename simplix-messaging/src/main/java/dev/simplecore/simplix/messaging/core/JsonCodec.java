package dev.simplecore.simplix.messaging.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON serialization/deserialization utility using Jackson.
 *
 * <p>Provides a shared {@link ObjectMapper} configured with:
 * <ul>
 *   <li>{@link JavaTimeModule} for Java 8 date/time types</li>
 *   <li>Unknown properties ignored during deserialization</li>
 *   <li>Timestamps written as ISO-8601 strings (not numeric)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * byte[] bytes = JsonCodec.serialize(myObject);
 * MyClass obj = JsonCodec.deserialize(bytes, MyClass.class);
 *
 * // Or use a custom ObjectMapper:
 * JsonCodec codec = new JsonCodec(customMapper);
 * byte[] bytes = codec.encode(myObject);
 * }</pre>
 */
public class JsonCodec {

    private static final ObjectMapper DEFAULT_MAPPER = createDefaultMapper();

    private final ObjectMapper objectMapper;

    /**
     * Create a codec with a custom {@link ObjectMapper}.
     *
     * @param objectMapper the Jackson ObjectMapper to use
     */
    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Create a codec with the default {@link ObjectMapper}.
     */
    public JsonCodec() {
        this(DEFAULT_MAPPER);
    }

    /**
     * Serialize an object to JSON bytes using the default ObjectMapper.
     *
     * @param value the object to serialize
     * @return the JSON byte array
     * @throws JsonCodecException if serialization fails
     */
    public static byte[] serialize(Object value) {
        try {
            return DEFAULT_MAPPER.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Deserialize JSON bytes to an object using the default ObjectMapper.
     *
     * @param bytes the JSON byte array
     * @param type  the target class
     * @param <T>   the target type
     * @return the deserialized object
     * @throws JsonCodecException if deserialization fails
     */
    public static <T> T deserialize(byte[] bytes, Class<T> type) {
        try {
            return DEFAULT_MAPPER.readValue(bytes, type);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to deserialize JSON to " + type.getName(), e);
        }
    }

    /**
     * Serialize an object to JSON bytes using this codec's ObjectMapper.
     *
     * @param value the object to serialize
     * @return the JSON byte array
     * @throws JsonCodecException if serialization fails
     */
    public byte[] encode(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Deserialize JSON bytes to an object using this codec's ObjectMapper.
     *
     * @param bytes the JSON byte array
     * @param type  the target class
     * @param <T>   the target type
     * @return the deserialized object
     * @throws JsonCodecException if deserialization fails
     */
    public <T> T decode(byte[] bytes, Class<T> type) {
        try {
            return objectMapper.readValue(bytes, type);
        } catch (Exception e) {
            throw new JsonCodecException("Failed to deserialize JSON to " + type.getName(), e);
        }
    }

    /**
     * Return the underlying ObjectMapper.
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Return the default shared ObjectMapper.
     */
    public static ObjectMapper defaultMapper() {
        return DEFAULT_MAPPER;
    }

    private static ObjectMapper createDefaultMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    /**
     * Exception thrown when JSON codec operations fail.
     */
    public static class JsonCodecException extends RuntimeException {

        public JsonCodecException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
