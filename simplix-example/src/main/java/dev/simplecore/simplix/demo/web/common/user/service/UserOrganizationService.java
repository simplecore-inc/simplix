package dev.simplecore.simplix.demo.web.common.user.service;

import dev.simplecore.simplix.demo.web.common.user.dto.UserOrganizationDTOs.*;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserOrganization;
import dev.simplecore.simplix.demo.domain.common.user.mapper.UserOrganizationTreeMapper;
import dev.simplecore.simplix.demo.domain.common.user.repository.UserOrganizationRepository;
import dev.simplecore.simplix.web.service.SimpliXBaseService;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.*;



/**
 * Service class for managing UserOrganization entities.
 * Provides business logic for CRUD operations, batch processing, and search functionality for UserOrganization entities.
 */
@Service
@Transactional
public class UserOrganizationService extends SimpliXBaseService<UserOrganization, String> {
    
    private UserOrganizationTreeMapper organizationTreeMapper;

    /**
     * Constructs a new UserOrganizationService with required dependencies.
     *
     * @param repository The repository for UserOrganization entities
     * @param entityManager JPA EntityManager for database operations
     */
    public UserOrganizationService(
        UserOrganizationRepository repository,
        EntityManager entityManager,
        UserOrganizationTreeMapper organizationTreeMapper
    ) {
        super(repository, entityManager);
        this.organizationTreeMapper = organizationTreeMapper;
    }

    /**
     * Creates a new UserOrganization entity from the provided DTO.
     *
     * @param createDTO DTO containing the data for the new UserOrganization
     * @return UserOrganizationDetailDTO of the created entity
     * @throws RuntimeException if related entities are not found
     */
    @Transactional
    public UserOrganizationDetailDTO create(UserOrganizationCreateDTO createDTO) {
        UserOrganization entity = new UserOrganization();
        modelMapper.map(createDTO, entity);
        return saveAndGetProjection(entity);
    }

    /**
     * Updates an existing UserOrganization entity with the provided DTO data.
     *
     * @param entity The existing UserOrganization entity to update
     * @param updateDto DTO containing the updated data
     * @return UserOrganizationDetailDTO of the updated entity
     * @throws RuntimeException if related entities are not found
     */
    @Transactional
    public UserOrganizationDetailDTO update(UserOrganization entity, UserOrganizationUpdateDTO updateDto) {
        modelMapper.map(updateDto, entity);
        return saveAndGetProjection(entity);
    }

    /**
     * Deletes a UserOrganization entity by its ID.
     *
     * @param id The ID of the UserOrganization to delete
     */
    @Transactional
    public void delete(String id) {
        deleteById(id);
    }

    /**
     * Deletes multiple UserOrganization entities by their IDs.
     *
     * @param ids List of UserOrganization IDs to delete
     */
    @Transactional
    public void batchDelete(List<String> ids) {
        deleteAllByIds(ids);
    }

    /**
     * Searches for UserOrganization entities using provided search parameters.
     *
     * @param params Map of search parameters and their values
     * @return Page of UserOrganizationListDTO matching the search criteria
     */
    public Page<UserOrganizationListDTO> search(Map<String, String> params) {
        SearchCondition<UserOrganizationSearchDTO> searchCondition =
                new SearchableParamsParser<UserOrganizationSearchDTO>(UserOrganizationSearchDTO.class).convert(params);
        return findAllWithSearch(searchCondition, UserOrganizationListDTO.class);
    }

    /**
     * Searches for UserOrganization entities using a SearchCondition object.
     *
     * @param searchCondition Search conditions for filtering UserOrganization entities
     * @return Page of UserOrganizationListDTO matching the search criteria
     */
    public Page<UserOrganizationListDTO> search(SearchCondition<UserOrganizationSearchDTO> searchCondition) {
        return findAllWithSearch(searchCondition, UserOrganizationListDTO.class);
    }

    /**
     * Updates multiple UserOrganization entities in a single operation.
     *
     * @param updateDtos Set of DTOs containing update data for multiple UserOrganizations
     * @return List of UserOrganizationDetailDTO for the updated entities
     * @throws RuntimeException if any related entities are not found
     */
    @Transactional
    public List<UserOrganizationDetailDTO> multiUpdate(Set<UserOrganizationUpdateDTO> updateDtos) {
        List<UserOrganizationDetailDTO> updatedEntities = new ArrayList<>();
        
        for (UserOrganizationUpdateDTO updateDto : updateDtos) {
            if (updateDto.getId() == null) {
                continue;
            }

            Optional<UserOrganization> entityOpt = findById(updateDto.getId());
            if (entityOpt.isPresent()) {
                UserOrganization entity = entityOpt.get();
                UserOrganizationDetailDTO updatedEntity = update(entity, updateDto);
                updatedEntities.add(updatedEntity);
            }
        }
        
        return updatedEntities;
    }

    /**
     * Performs batch updates on multiple UserOrganization entities.
     *
     * @param dto DTO containing the batch update data and target IDs
     * @throws RuntimeException if any related entities are not found
     */
    @Transactional
    public void batchUpdate(UserOrganizationBatchUpdateDTO dto) {
        List<UserOrganization> entities = findAllById(dto.getIds());
        
        entities.forEach(entity -> {
            
            if (dto.getOrgType() != null) {
                entity.setOrgType(dto.getOrgType());
            }
            
            if (dto.getDescription() != null) {
                entity.setDescription(dto.getDescription());
            }
            
            if (dto.getItemOrder() != null) {
                entity.setItemOrder(dto.getItemOrder());
            }

            if (dto.getParent() != null) {
            }
            
            if (dto.getChildren() != null) {
            }
            
        });
        
        saveAll(entities);
    }

    //----------------------------------

    /**
     * Saves a UserOrganization entity and returns its projection.
     * Handles the relationships with other entities.
     *
     * @param entity The UserOrganization entity to save
     * @param dto The DTO containing relationship data
     * @return UserOrganizationDetailDTO of the saved entity
     * @throws RuntimeException if saving fails or if related entities are not found
     */
    private UserOrganizationDetailDTO saveAndGetProjection(UserOrganization entity) {
        UserOrganization savedEntity = saveAndFlush(entity);
        return findById(savedEntity.getId(), UserOrganizationDetailDTO.class)
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
        // UserOrganization entity = new UserOrganization();
        // if (id != null) {
        //     entity = findById(id).orElseThrow(() -> new RuntimeException("UserOrganization not found"));
        // } else {
        //     modelMapper.map(dto, entity);
        // }

        // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // if (Set.of("view", "edit", "delete").contains(permission)) {
        //     return auth.getName().equals(entity.getUsername());
        // }

        throw new UnsupportedOperationException("Unimplemented method 'hasPermission'");
    }




    //----------------------------------
    // MyBatis
    //----------------------------------

    /**
     * Retrieve organization tree (full or subtree)
     */
    public List<UserOrganizationTreeDTO> getOrganizationTree(String orgId) {
        List<UserOrganizationTreeDTO> flatList = organizationTreeMapper.organizationTree(orgId);
        return buildOrganizationTree(flatList);
    }

    /**
     * Retrieve entire organization tree
     */
    public List<UserOrganizationTreeDTO> getOrganizationTree() {
        return getOrganizationTree(null);
    }

    /**
     * Retrieve subtree with a specific organization as root
     */
    public List<UserOrganizationTreeDTO> getOrganizationSubTree(String orgId) {
        return getOrganizationTree(orgId);
    }

    /**
     * Retrieve all subordinate organization IDs for a specific organization
     */
    public List<String> getAllChildrenIds(String orgId) {
        return organizationTreeMapper.findAllChildrenIds(orgId);
    }

    /**
     * Retrieve all parent organization IDs for a specific organization
     */
    public List<String> getAllParentIds(String orgId) {
        return organizationTreeMapper.findAllParentIds(orgId);
    }

    /**
     * Retrieve direct subordinate organizations for a specific organization
     */
    public List<UserOrganizationMyBatisDTO> getDirectChildren(String orgId) {
        return organizationTreeMapper.findDirectChildren(orgId);
    }

    /**
     * Retrieve direct parent organization of a specific organization
     */
    public UserOrganizationMyBatisDTO getDirectParent(String orgId) {
        return organizationTreeMapper.findDirectParent(orgId);
    }

    /**
     * Convert flat list of organizations to tree structure
     */
    private List<UserOrganizationTreeDTO> buildOrganizationTree(List<UserOrganizationTreeDTO> flatList) {
        Map<String, UserOrganizationTreeDTO> nodeMap = new HashMap<>();
        List<UserOrganizationTreeDTO> rootNodes = new ArrayList<>();

        // Store all nodes in a map
        for (UserOrganizationTreeDTO node : flatList) {
            nodeMap.put(node.getId(), node);
        }

        // Set parent-child relationships
        for (UserOrganizationTreeDTO node : flatList) {
            if (node.getParentId() == null || !nodeMap.containsKey(node.getParentId())) {
                rootNodes.add(node);
            } else {
                UserOrganizationTreeDTO parent = nodeMap.get(node.getParentId());
                parent.getChildren().add(node);
            }
        }

        return rootNodes;
    }

    /**
     * Check if a specific organization is an ancestor of another organization
     */
    public boolean isAncestorOf(String ancestorId, String descendantId) {
        List<String> parentIds = getAllParentIds(descendantId);
        return parentIds.contains(ancestorId);
    }

    /**
     * Check if a specific organization is a descendant of another organization
     */
    public boolean isDescendantOf(String descendantId, String ancestorId) {
        List<String> childrenIds = getAllChildrenIds(ancestorId);
        return childrenIds.contains(descendantId);
    }
} 