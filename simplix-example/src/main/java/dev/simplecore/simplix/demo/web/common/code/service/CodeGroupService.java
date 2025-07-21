package dev.simplecore.simplix.demo.web.common.code.service;

import dev.simplecore.simplix.demo.domain.common.code.entity.CodeGroup;
import dev.simplecore.simplix.demo.domain.common.code.repository.CodeGroupRepository;
import dev.simplecore.simplix.demo.web.common.code.dto.CodeGroupDTOs.*;

import dev.simplecore.simplix.web.service.SimpliXBaseService;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.*;


/**
 * Service class for managing CodeGroup entities.
 * Provides business logic for CRUD operations, batch processing, and search functionality for CodeGroup entities.

 */
@Service
@Transactional(readOnly = true)
public class CodeGroupService extends SimpliXBaseService<CodeGroup, String> {
    
    /**
     * Constructs a new CodeGroupService with required dependencies.
     *
     * @param repository The repository for CodeGroup entities
     * @param entityManager JPA EntityManager for database operations
     */
    public CodeGroupService(
        CodeGroupRepository repository,
        EntityManager entityManager
    ) {
        super(repository, entityManager);
    }


    /**
     * Searches for CodeGroup entities using provided search parameters.
     *
     * @param params Map of search parameters and their values
     * @return Page of CodeGroupListDTO matching the search criteria
     */
    public Page<CodeGroupListDTO> search(Map<String, String> params) {
        SearchCondition<CodeGroupSearchDTO> searchCondition =
                new SearchableParamsParser<CodeGroupSearchDTO>(CodeGroupSearchDTO.class).convert(params);
        return findAllWithSearch(searchCondition, CodeGroupListDTO.class);
    }

    /**
     * Searches for CodeGroup entities using a SearchCondition object.
     *
     * @param searchCondition Search conditions for filtering CodeGroup entities
     * @return Page of CodeGroupListDTO matching the search criteria
     */
    public Page<CodeGroupListDTO> search(SearchCondition<CodeGroupSearchDTO> searchCondition) {
        return findAllWithSearch(searchCondition, CodeGroupListDTO.class);
    }
    
    //----------------------------------



    /**
     * Updates the order of a CodeGroup entity.
     *
     * @param orderUpdateDto DTO containing the ID and new order value
     * @return CodeGroupDetailDTO of the updated entity
     * @throws RuntimeException if the entity is not found
     */
    @Transactional
    public CodeGroupDetailDTO updateOrder(CodeGroupOrderUpdateDTO orderUpdateDto) {
        CodeGroup entity = findById(orderUpdateDto.getCodeGroupId())
            .orElseThrow(() -> new RuntimeException("CodeGroup not found"));
        
        entity.setSortOrder(orderUpdateDto.getSortOrder());

        CodeGroup savedEntity = saveAndFlush(entity);
        return findById(savedEntity.getId(), CodeGroupDetailDTO.class)
            .orElseThrow(() -> new RuntimeException("Failed to retrieve saved entity"));
    }

    //----------------------------------

    /**
     * Checks if the current user has the specified permission for the entity.
     *
     * @param permission The permission to check (e.g., "view", "edit", "delete")
     * @param codeGroupId The ID of the entity to check permissions for. Can be null for new entities
     * @param dto The DTO object containing entity data. Used when codeGroupId is null
     * @return true if the user has the permission, false otherwise
     * @throws RuntimeException if the entity is not found with the given codeGroupId
     */
    @Override
    public boolean hasOwnerPermission(String permission, String codeGroupId, Object dto) {
        // TODO must be implemented

        // // Example implementation
        // CodeGroup entity = new CodeGroup();
        // if (codeGroupId != null) {
        //     entity = findById(codeGroupId).orElseThrow(() -> new RuntimeException("CodeGroup not found"));
        // } else {
        //     modelMapper.map(dto, entity);
        // }

        // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // if (Set.of("view", "edit", "delete").contains(permission)) {
        //     return auth.getName().equals(entity.getUsername());
        // }

        throw new UnsupportedOperationException("Unimplemented method 'hasPermission'");
    }
}
