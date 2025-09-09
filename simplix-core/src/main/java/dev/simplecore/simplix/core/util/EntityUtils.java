package dev.simplecore.simplix.core.util;

import jakarta.persistence.Id;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;

public class EntityUtils {
    
    @SuppressWarnings("unchecked")
    public static <E> Class<E> getEntityClass(Class<?> clazz) {
        return (Class<E>) ((ParameterizedType) clazz
            .getGenericSuperclass()).getActualTypeArguments()[0];
    }

    @SuppressWarnings("unchecked")
    public static <E> E convertToEntity(Object source, Class<E> entityClass) {
        if (source.getClass() != entityClass) {
            ModelMapper mapper = new ModelMapper();
            mapper.getConfiguration()
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(org.modelmapper.config.Configuration.AccessLevel.PRIVATE)
                .setPropertyCondition(ctx -> ctx.getSource() != null)
                .setMatchingStrategy(MatchingStrategies.STRICT);
                
            return mapper.map(source, entityClass);
        } else {
            return (E) source;
        }
    }

    @SuppressWarnings("unchecked")
    public static <E, ID> ID getEntityId(E entity) {
        try {
            Field[] fields = entity.getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(Id.class)) {
                    field.setAccessible(true);
                    return (ID) field.get(entity);
                }
            }
            throw new RuntimeException("No @Id field found in entity");
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get entity ID", e);
        }
    }
} 