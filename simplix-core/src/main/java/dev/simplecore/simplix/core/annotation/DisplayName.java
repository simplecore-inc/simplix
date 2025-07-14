package dev.simplecore.simplix.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a field as the display name/title field of an entity.
 * This field is typically used to represent the entity in UI components like dropdowns,
 * lists, and references.
 * 
 * Usage example:
 * @DisplayName
 * private String name;
 * 
 * @DisplayName(priority = 1)
 * private String title;
 * 
 * If multiple fields are marked with @DisplayName, the one with the highest priority
 * (lowest number) will be used as the primary display name.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DisplayName {
    
    /**
     * Priority of this display name field.
     * Lower numbers indicate higher priority.
     * Default is 0 (highest priority).
     * 
     * @return the priority value
     */
    int priority() default 0;
    
    /**
     * Optional description of what this display name represents.
     * 
     * @return the description
     */
    String description() default "";
} 