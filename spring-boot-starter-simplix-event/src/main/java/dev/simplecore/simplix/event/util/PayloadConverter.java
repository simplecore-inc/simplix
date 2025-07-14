package dev.simplecore.simplix.event.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Utility class for event payload conversion
 * Messages transmitted through message brokers like RabbitMQ are often deserialized as Maps,
 * so this provides functionality to convert them to the desired type.
 */
@Component
public class PayloadConverter {
    
    private final ObjectMapper objectMapper;
    
    public PayloadConverter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Converts a payload object to the specified type.
     * 
     * @param payload The payload object to convert (Map or original type)
     * @param targetType The class of the target type to convert to
     * @param <T> The target type
     * @return The converted object
     */
    public <T> T convertPayload(Object payload, Class<T> targetType) {
        if (payload == null) {
            return null;
        }
        
        // If already the target type, return as is
        if (targetType.isInstance(payload)) {
            return targetType.cast(payload);
        }
        
        // If it's a Map, use ObjectMapper to convert
        if (payload instanceof Map) {
            return objectMapper.convertValue(payload, targetType);
        }
        
        // Otherwise, throw an exception
        throw new IllegalArgumentException(
            String.format("Cannot convert payload of type %s to %s", 
                payload.getClass().getName(), targetType.getName())
        );
    }
    
    /**
     * Checks if a payload object can be converted to the specified type.
     * 
     * @param payload The payload object to check
     * @param targetType The class of the target type to check against
     * @return Whether conversion is possible
     */
    public boolean canConvert(Object payload, Class<?> targetType) {
        if (payload == null) {
            return true;
        }
        
        return targetType.isInstance(payload) || payload instanceof Map;
    }
}