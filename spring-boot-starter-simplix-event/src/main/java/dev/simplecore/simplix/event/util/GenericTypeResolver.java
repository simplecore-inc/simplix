package dev.simplecore.simplix.event.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Utility class for extracting generic type parameters
 */
public class GenericTypeResolver {
    
    /**
     * Extracts the generic type parameter from a class.
     * 
     * @param clazz Target class
     * @param genericInterface Generic interface
     * @return Class of the generic type parameter
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> resolveGenericType(Class<?> clazz, Class<?> genericInterface) {
        // Find generic type from interfaces
        Type[] genericInterfaces = clazz.getGenericInterfaces();
        for (Type type : genericInterfaces) {
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                if (parameterizedType.getRawType().equals(genericInterface)) {
                    Type[] typeArguments = parameterizedType.getActualTypeArguments();
                    if (typeArguments.length > 0 && typeArguments[0] instanceof Class) {
                        return (Class<T>) typeArguments[0];
                    }
                }
            }
        }
        
        // Also check superclass
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            return resolveGenericType(superClass, genericInterface);
        }
        
        // Return Object.class if type cannot be found
        return (Class<T>) Object.class;
    }
} 