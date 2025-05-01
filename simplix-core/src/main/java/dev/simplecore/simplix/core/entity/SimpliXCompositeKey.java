package dev.simplecore.simplix.core.entity;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Interface for composite keys.
 * Entity classes with composite keys should implement this interface for their @EmbeddedId field.
 * 
 * In addition to implementing the instance methods, classes MUST also provide:
 * - static fromUrlPath(String... pathVariables): creates key from URL path variables
 * - static fromString(String compositeId): creates key from its string representation
 */
public interface SimpliXCompositeKey extends Serializable {
    
    /**
     * Populates this key instance from URL path variables.
     * WARNING: This instance method is required to satisfy the interface contract,
     * but the actual factory should be done via a static method in the class:
     * public static YourKeyType fromUrlPath(String... pathVariables) { ... }
     *
     * @param pathVariables URL path variables as string array
     * @return This instance with values set from path variables
     * @throws IllegalArgumentException if path variables are invalid
     */
    SimpliXCompositeKey fromPathVariables(String... pathVariables);
    
    /**
     * Populates this key instance from a string representation.
     * WARNING: This instance method is required to satisfy the interface contract,
     * but the actual factory should be done via a static method in the class:
     * public static YourKeyType fromString(String compositeId) { ... }
     *
     * @param compositeId String representation of the composite key
     * @return This instance with values set from the composite ID string
     * @throws IllegalArgumentException if string format is invalid
     */
    SimpliXCompositeKey fromCompositeId(String compositeId);
    
    /**
     * Validates this composite key and sets default values if necessary.
     * This should be called after setting the key's components or during the entity lifecycle.
     * 
     * @throws RuntimeException if the key is invalid
     */
    void validate();
    
    /**
     * Default string representation of the composite key.
     * Use this to implement toString() in your composite key class.
     * This method scans all fields of the key and joins them with "__" delimiter.
     * 
     * @return String with fields joined by "__" delimiter
     */
    default String toCompositeKeyString() {
        try {
            Class<?> keyClass = this.getClass();
            Field[] fields = keyClass.getDeclaredFields();
            List<Object> values = new ArrayList<>();
            
            for (Field field : fields) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue; // Skip static fields
                }
                
                field.setAccessible(true);
                values.add(field.get(this));
            }
            
            StringJoiner joiner = new StringJoiner("__");
            for (Object value : values) {
                joiner.add(Objects.toString(value, "null"));
            }
            return joiner.toString();
        } catch (Exception e) {
            return this.getClass().getSimpleName() + "@" + Integer.toHexString(this.hashCode());
        }
    }
} 