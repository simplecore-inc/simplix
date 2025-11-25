package dev.simplecore.simplix.hibernate.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define cache eviction policy for an entity
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheEvictionPolicy {

    /**
     * Fields that trigger cache eviction when changed
     */
    String[] evictOnChange() default {};

    /**
     * Fields that DO NOT trigger cache eviction
     */
    String[] ignoreFields() default {"lastModifiedDate", "version", "updatedBy"};

    /**
     * Whether to evict query cache on update
     */
    boolean evictQueryCache() default true;

    /**
     * Custom eviction strategy class
     */
    Class<? extends EvictionStrategy> strategy() default DefaultEvictionStrategy.class;

    interface EvictionStrategy {
        boolean shouldEvict(Object entity, String[] dirtyFields);
    }

    class DefaultEvictionStrategy implements EvictionStrategy {
        @Override
        public boolean shouldEvict(Object entity, String[] dirtyFields) {
            return dirtyFields != null && dirtyFields.length > 0;
        }
    }
}