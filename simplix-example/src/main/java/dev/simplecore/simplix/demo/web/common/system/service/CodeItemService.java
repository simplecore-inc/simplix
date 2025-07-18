package dev.simplecore.simplix.demo.web.common.system.service;

import dev.simplecore.simplix.demo.domain.common.system.entity.CodeItem;
import dev.simplecore.simplix.demo.domain.common.system.repository.CodeItemRepository;
import dev.simplecore.simplix.demo.web.common.system.dto.CodeItemDTOs.*;
import dev.simplecore.simplix.demo.web.common.system.service.CodeItemService;

import dev.simplecore.simplix.web.service.SimpliXBaseService;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Service class for managing CodeItem entities.
 * Provides business logic for CRUD operations, batch processing, and search functionality for CodeItem entities.

 */
@Service
@Transactional(readOnly = true)
public class CodeItemService extends SimpliXBaseService<CodeItem, String> {
    
    /**
     * Constructs a new CodeItemService with required dependencies.
     *
     * @param repository The repository for CodeItem entities
     * @param entityManager JPA EntityManager for database operations
     * @param codeItemTreeServiceProvider Provider for CodeItem Tree service
     */
    public CodeItemService(
        CodeItemRepository repository,
        EntityManager entityManager
    ) {
        super(repository, entityManager);
    }


    /**
     * Searches for CodeItem entities using provided search parameters.
     *
     * @param params Map of search parameters and their values
     * @return Page of CodeItemListDTO matching the search criteria
     */
    public Page<CodeItemListDTO> search(Map<String, String> params) {
        SearchCondition<CodeItemSearchDTO> searchCondition =
                new SearchableParamsParser<CodeItemSearchDTO>(CodeItemSearchDTO.class).convert(params);
        return findAllWithSearch(searchCondition, CodeItemListDTO.class);
    }

    /**
     * Searches for CodeItem entities using a SearchCondition object.
     *
     * @param searchCondition Search conditions for filtering CodeItem entities
     * @return Page of CodeItemListDTO matching the search criteria
     */
    public Page<CodeItemListDTO> search(SearchCondition<CodeItemSearchDTO> searchCondition) {
        return findAllWithSearch(searchCondition, CodeItemListDTO.class);
    }

    //----------------------------------

    /**
     * Checks if the current user has the specified permission for the entity.
     *
     * @param permission The permission to check (e.g., "view", "edit", "delete")
     * @param codeId The ID of the entity to check permissions for. Can be null for new entities
     * @param dto The DTO object containing entity data. Used when codeId is null
     * @return true if the user has the permission, false otherwise
     * @throws RuntimeException if the entity is not found with the given codeId
     */
    @Override
    public boolean hasOwnerPermission(String permission, String codeId, Object dto) {
        // TODO must be implemented

        // // Example implementation
        // CodeItem entity = new CodeItem();
        // if (codeId != null) {
        //     entity = findById(codeId).orElseThrow(() -> new RuntimeException("CodeItem not found"));
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
