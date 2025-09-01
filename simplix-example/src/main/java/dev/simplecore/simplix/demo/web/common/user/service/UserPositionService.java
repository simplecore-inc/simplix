package dev.simplecore.simplix.demo.web.common.user.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserPosition;
import dev.simplecore.simplix.demo.domain.common.user.repository.UserPositionRepository;
import dev.simplecore.simplix.demo.web.common.user.dto.UserPositionDTOs.*;
import dev.simplecore.simplix.web.service.SimpliXBaseService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.*;



/**
 * Service class for managing UserPosition entities.
 * Provides business logic for CRUD operations, batch processing, and search functionality for UserPosition entities.
 */
@Service
@Transactional
public class UserPositionService extends SimpliXBaseService<UserPosition, String> {
    
    
    /**
     * Constructs a new UserPositionService with required dependencies.
     *
     * @param repository The repository for UserPosition entities
     * @param entityManager JPA EntityManager for database operations
     */
    public UserPositionService(
        UserPositionRepository repository,
        EntityManager entityManager
    ) {
        super(repository, entityManager);
        
    }

    /**
     * Creates a new UserPosition entity from the provided DTO.
     *
     * @param createDTO DTO containing the data for the new UserPosition
     * @return UserPositionDetailDTO of the created entity
     * @throws RuntimeException if related entities are not found
     */
    @Transactional
    public UserPositionDetailDTO create(UserPositionCreateDTO createDTO) {
        UserPosition entity = new UserPosition();
        modelMapper.map(createDTO, entity);
        return saveAndGetProjection(entity);
    }

    /**
     * Updates an existing UserPosition entity with the provided DTO data.
     *
     * @param entity The existing UserPosition entity to update
     * @param updateDto DTO containing the updated data
     * @return UserPositionDetailDTO of the updated entity
     * @throws RuntimeException if related entities are not found
     */
    @Transactional
    public UserPositionDetailDTO update(UserPosition entity, UserPositionUpdateDTO updateDto) {
        modelMapper.map(updateDto, entity);
        return saveAndGetProjection(entity);
    }

    /**
     * Deletes a UserPosition entity by its ID.
     *
     * @param id The ID of the UserPosition to delete
     */
    @Transactional
    public void delete(String id) {
        deleteById(id);
    }

    /**
     * Deletes multiple UserPosition entities by their IDs.
     *
     * @param ids List of UserPosition IDs to delete
     */
    @Transactional
    public void batchDelete(List<String> ids) {
        deleteAllByIds(ids);
    }

    /**
     * Searches for UserPosition entities using provided search parameters.
     *
     * @param params Map of search parameters and their values
     * @return Page of UserPositionListDTO matching the search criteria
     */
    public Page<UserPositionListDTO> search(Map<String, String> params) {
        SearchCondition<UserPositionSearchDTO> searchCondition =
                new SearchableParamsParser<UserPositionSearchDTO>(UserPositionSearchDTO.class).convert(params);
        return findAllWithSearch(searchCondition, UserPositionListDTO.class);
    }

    /**
     * Searches for UserPosition entities using a SearchCondition object.
     *
     * @param searchCondition Search conditions for filtering UserPosition entities
     * @return Page of UserPositionListDTO matching the search criteria
     */
    public Page<UserPositionListDTO> search(SearchCondition<UserPositionSearchDTO> searchCondition) {
        return findAllWithSearch(searchCondition, UserPositionListDTO.class);
    }

    /**
     * Updates multiple UserPosition entities in a single operation.
     *
     * @param updateDtos Set of DTOs containing update data for multiple UserPositions
     * @return List of UserPositionDetailDTO for the updated entities
     * @throws RuntimeException if any related entities are not found
     */
    @Transactional
    public List<UserPositionDetailDTO> multiUpdate(Set<UserPositionUpdateDTO> updateDtos) {
        List<UserPositionDetailDTO> updatedEntities = new ArrayList<>();
        
        for (UserPositionUpdateDTO updateDto : updateDtos) {
            if (updateDto.getId() == null) {
                continue;
            }

            Optional<UserPosition> entityOpt = findById(updateDto.getId());
            if (entityOpt.isPresent()) {
                UserPosition entity = entityOpt.get();
                UserPositionDetailDTO updatedEntity = update(entity, updateDto);
                updatedEntities.add(updatedEntity);
            }
        }
        
        return updatedEntities;
    }

    /**
     * Performs batch updates on multiple UserPosition entities.
     *
     * @param dto DTO containing the batch update data and target IDs
     * @throws RuntimeException if any related entities are not found
     */
    @Transactional
    public void batchUpdate(UserPositionBatchUpdateDTO dto) {
        List<UserPosition> entities = findAllById(dto.getIds());
        
        entities.forEach(entity -> {
            
        });
        
        saveAll(entities);
    }

    //----------------------------------

    /**
     * Saves a UserPosition entity and returns its projection.
     * Handles the relationships with other entities.
     *
     * @param entity The UserPosition entity to save
     * @param dto The DTO containing relationship data
     * @return UserPositionDetailDTO of the saved entity
     * @throws RuntimeException if saving fails or if related entities are not found
     */
    private UserPositionDetailDTO saveAndGetProjection(UserPosition entity) {
        UserPosition savedEntity = saveAndFlush(entity);
        return findById(savedEntity.getId(), UserPositionDetailDTO.class)
            .orElseThrow(() -> new RuntimeException("Failed to retrieve saved entity"));
    }

    //----------------------------------
    
    /**
     * Checks if the current user has the specified permission for the entity.
     * 
     * @param permission The permission to check (e.g., "view", "edit", "delete")
     * @param id The ID of the entity to check permissions for. Can be null for new entities
     * @param dto The DTO object containing entity data. Used when id is null
     * @return true if the user has the permission, false otherwise
     * @throws RuntimeException if the entity is not found with the given id
     */
    @Override
    public boolean hasOwnerPermission(String permission, String id, Object dto) {
        // TODO must be implemented

        // // Example implementation
        // UserPosition entity = new UserPosition();
        // if (id != null) {
        //     entity = findById(id).orElseThrow(() -> new RuntimeException("UserPosition not found"));
        // } else {
        //     modelMapper.map(dto, entity);
        // }

        // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // if (Set.of("view", "edit", "delete").contains(permission)) {
        //     return auth.getName().equals(entity.getUsername());
        // }

        throw new UnsupportedOperationException("Unimplemented method 'hasPermission'");
    }

    /**
     * Validate position ID
     */
    public boolean validateId(String id) {
        if (id == null) return false;
        return existsById(id);
    }
} 