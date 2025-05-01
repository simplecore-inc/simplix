package dev.simplecore.simplix.core.util;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for converting between entities and DTOs
 */
public class DtoUtils {
    private static final ModelMapper modelMapper = new ModelMapper();

    /**
     * Convert an entity to a DTO
     * 
     * @param entity Source entity to convert
     * @param dtoClass Target DTO class
     * @return Converted DTO instance
     */
    public static <E, D> D toDto(E entity, Class<D> dtoClass) {
        return modelMapper.map(entity, dtoClass);
    }

    /**
     * Convert a list of entities to a list of DTOs
     * 
     * @param entities List of source entities
     * @param dtoClass Target DTO class
     * @return List of converted DTO instances
     */
    public static <E, D> List<D> toDtoList(List<E> entities, Class<D> dtoClass) {
        return entities.stream()
            .map(entity -> toDto(entity, dtoClass))
            .collect(Collectors.toList());
    }

    /**
     * Convert a page of entities to a page of DTOs
     * 
     * @param entityPage Page of source entities
     * @param dtoClass Target DTO class
     * @return Page of converted DTO instances
     */
    public static <E, D> Page<D> toDtoPage(Page<E> entityPage, Class<D> dtoClass) {
        List<D> dtos = toDtoList(entityPage.getContent(), dtoClass);
        return new PageImpl<>(dtos, entityPage.getPageable(), entityPage.getTotalElements());
    }
} 