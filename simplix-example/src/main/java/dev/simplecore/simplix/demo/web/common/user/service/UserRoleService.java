package dev.simplecore.simplix.demo.web.common.user.service;

import dev.simplecore.simplix.demo.web.common.user.dto.UserRoleDTOs.*;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserRole;
import dev.simplecore.simplix.demo.domain.common.user.repository.UserRoleRepository;
import dev.simplecore.simplix.web.service.SimpliXBaseService;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.*;




@Service
@Transactional
public class UserRoleService extends SimpliXBaseService<UserRole, String> {
    
    
    /**
     * Constructs a new UserRoleService with required dependencies.
     *
     * @param repository The repository for UserRole entities
     * @param entityManager JPA EntityManager for database operations
     */
    public UserRoleService(
        UserRoleRepository repository,
        EntityManager entityManager
    ) {
        super(repository, entityManager);
        
    }

    /**
     * Creates a new UserRole entity from the provided DTO.
     *
     * @param createDTO DTO containing the data for the new UserRole
     * @return UserRoleDetailDTO of the created entity
     * @throws RuntimeException if related entities are not found
     */
    @Transactional
    public UserRoleDetailDTO create(UserRoleCreateDTO createDTO) {
        UserRole entity = new UserRole();
        modelMapper.map(createDTO, entity);
        return saveAndGetProjection(entity);
    }

    /**
     * Updates an existing UserRole entity with the provided DTO data.
     *
     * @param entity The existing UserRole entity to update
     * @param updateDto DTO containing the updated data
     * @return UserRoleDetailDTO of the updated entity
     * @throws RuntimeException if related entities are not found
     */
    @Transactional
    public UserRoleDetailDTO update(UserRole entity, UserRoleUpdateDTO updateDto) {
        modelMapper.map(updateDto, entity);
        return saveAndGetProjection(entity);
    }

    /**
     * Deletes a UserRole entity by its ID.
     *
     * @param id The ID of the UserRole to delete
     */
    @Transactional
    public void delete(String id) {
        deleteById(id);
    }

    /**
     * Deletes multiple UserRole entities by their IDs.
     *
     * @param ids List of UserRole IDs to delete
     */
    @Transactional
    public void batchDelete(List<String> ids) {
        deleteAllByIds(ids);
    }

    /**
     * Searches for UserRole entities using provided search parameters.
     *
     * @param params Map of search parameters and their values
     * @return Page of UserRoleListDTO matching the search criteria
     */
    public Page<UserRoleListDTO> search(Map<String, String> params) {
        SearchCondition<UserRoleSearchDTO> searchCondition =
                new SearchableParamsParser<UserRoleSearchDTO>(UserRoleSearchDTO.class).convert(params);
        return findAllWithSearch(searchCondition, UserRoleListDTO.class);
    }

    /**
     * Searches for UserRole entities using a SearchCondition object.
     *
     * @param searchCondition Search conditions for filtering UserRole entities
     * @return Page of UserRoleListDTO matching the search criteria
     */
    public Page<UserRoleListDTO> search(SearchCondition<UserRoleSearchDTO> searchCondition) {
        return findAllWithSearch(searchCondition, UserRoleListDTO.class);
    }

    /**
     * Updates multiple UserRole entities in a single operation.
     *
     * @param updateDtos Set of DTOs containing update data for multiple UserRoles
     * @return List of UserRoleDetailDTO for the updated entities
     * @throws RuntimeException if any related entities are not found
     */
    @Transactional
    public List<UserRoleDetailDTO> multiUpdate(Set<UserRoleUpdateDTO> updateDtos) {
        List<UserRoleDetailDTO> updatedEntities = new ArrayList<>();
        
        for (UserRoleUpdateDTO updateDto : updateDtos) {
            if (updateDto.getId() == null) {
                continue;
            }

            Optional<UserRole> entityOpt = findById(updateDto.getId());
            if (entityOpt.isPresent()) {
                UserRole entity = entityOpt.get();
                UserRoleDetailDTO updatedEntity = update(entity, updateDto);
                updatedEntities.add(updatedEntity);
            }
        }
        
        return updatedEntities;
    }

    /**
     * Performs batch updates on multiple UserRole entities.
     *
     * @param dto DTO containing the batch update data and target IDs
     * @throws RuntimeException if any related entities are not found
     */
    @Transactional
    public void batchUpdate(UserRoleBatchUpdateDTO dto) {
        List<UserRole> entities = findAllById(dto.getIds());
        
        entities.forEach(entity -> {
            
        });
        
        saveAll(entities);
    }

    //----------------------------------

    /**
     * Saves a UserRole entity and returns its projection.
     * Handles the relationships with other entities.
     *
     * @param entity The UserRole entity to save
     * @param dto The DTO containing relationship data
     * @return UserRoleDetailDTO of the saved entity
     * @throws RuntimeException if saving fails or if related entities are not found
     */
    private UserRoleDetailDTO saveAndGetProjection(UserRole entity) {
        UserRole savedEntity = saveAndFlush(entity);
        return findById(savedEntity.getId(), UserRoleDetailDTO.class)
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
        // UserRole entity = new UserRole();
        // if (id != null) {
        //     entity = findById(id).orElseThrow(() -> new RuntimeException("UserRole not found"));
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