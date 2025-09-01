package dev.simplecore.simplix.demo.web.common.code.service;

import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepository;
import dev.simplecore.simplix.core.tree.service.SimpliXTreeBaseService;
import dev.simplecore.simplix.demo.domain.common.code.entity.CodeGroup;
import dev.simplecore.simplix.demo.web.common.code.dto.CodeGroupDTOs;
import dev.simplecore.simplix.demo.web.common.code.dto.CodeGroupDTOs.CodeGroupBatchUpdateDTO;
import dev.simplecore.simplix.demo.web.common.code.dto.CodeGroupDTOs.CodeGroupCreateDTO;
import dev.simplecore.simplix.demo.web.common.code.dto.CodeGroupDTOs.CodeGroupDetailDTO;
import dev.simplecore.simplix.demo.web.common.code.dto.CodeGroupDTOs.CodeGroupListDTO;
import dev.simplecore.simplix.demo.web.common.code.dto.CodeGroupDTOs.CodeGroupUpdateDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.modelmapper.ModelMapper;

@Service
@Transactional
public class CodeGroupTreeService extends SimpliXTreeBaseService<CodeGroup, String> {

    private final ModelMapper modelMapper;

    private final SimpliXTreeRepository<CodeGroup, String> repository;

    public CodeGroupTreeService(SimpliXTreeRepository<CodeGroup, String> simpliXTreeRepository, ModelMapper modelMapper) {
        super(simpliXTreeRepository);
        this.modelMapper = modelMapper;
        this.repository = simpliXTreeRepository;
    }


    /**
     * Creates a new CodeGroup entity from the provided DTO.
     *
     * @param createDTO DTO containing the data for the new CodeGroup
     * @return CodeGroupDetailDTO of the created entity
     * @throws RuntimeException if related entities are not found
     */
    @Transactional
    public CodeGroupDetailDTO create(CodeGroupCreateDTO createDTO) {
        CodeGroup entity = new CodeGroup();
        modelMapper.map(createDTO, entity);
        if (entity.getParentId() == null || entity.getParentId().trim().isEmpty()) { entity.setParentId(null);}

        CodeGroup savedEntity = super.create(entity);
        return super.findById(savedEntity.getId())
            .map(e -> modelMapper.map(e, CodeGroupDetailDTO.class))
            .orElseThrow(() -> new RuntimeException("Failed to retrieve saved entity"));
    }


    /**
     * Updates an existing CodeGroup entity with the provided DTO data.
     *
     * @param entity The existing CodeGroup entity to update
     * @param updateDto DTO containing the updated data
     * @return CodeGroupDetailDTO of the updated entity
     * @throws RuntimeException if related entities are not found
     */
    @Transactional
    public CodeGroupDetailDTO update(CodeGroup entity, CodeGroupUpdateDTO updateDto) {
        modelMapper.map(updateDto, entity);
        if (entity.getParentId() == null || entity.getParentId().trim().isEmpty()) { entity.setParentId(null);}

        CodeGroup savedEntity = super.update(entity);
        return super.findById(savedEntity.getId())
            .map(e -> modelMapper.map(e, CodeGroupDetailDTO.class))
            .orElseThrow(() -> new RuntimeException("Failed to retrieve saved entity"));
    }


    /**
     * Updates multiple CodeGroup entities in a single operation.
     *
     * @param updateDtos Set of DTOs containing update data for multiple CodeGroups
     * @return List of CodeGroupDetailDTO for the updated entities
     * @throws RuntimeException if any related entities are not found
     */
    @Transactional
    public List<CodeGroupDetailDTO> multiUpdate(Set<CodeGroupUpdateDTO> updateDtos) {
        List<CodeGroupDetailDTO> updatedEntities = new ArrayList<>();

        for (CodeGroupUpdateDTO updateDto : updateDtos) {
            if (updateDto.getCodeGroupId() == null) {
                continue;
            }

            Optional<CodeGroup> entityOpt = super.findById(updateDto.getCodeGroupId());
            if (entityOpt.isPresent()) {
                CodeGroup entity = entityOpt.get();
                if (entity.getParentId() == null || entity.getParentId().trim().isEmpty()) { entity.setParentId(null);}

                CodeGroupDetailDTO updatedEntity = update(entity, updateDto);
                updatedEntities.add(updatedEntity);
            }
        }

        return updatedEntities;
    }


    /**
     * Deletes a CodeGroup entity by its ID.
     *
     * @param codeGroupId The ID of the CodeGroup to delete
     * @throws IllegalArgumentException if the entity is not found
     * @throws IllegalStateException if the entity has children
     */
    @Transactional
    public void delete(String codeGroupId) {
        super.deleteById(codeGroupId);
    }



    /**
     * Deletes multiple CodeGroup entities by their IDs.
     *
     * @param codeGroupIds List of CodeGroup IDs to delete
     */
    @Transactional
    public void batchDelete(List<String> codeGroupIds) {
        super.deleteBatch(codeGroupIds);
    }


        /**
     * Performs batch updates on multiple CodeGroup entities.
     *
     * @param dto DTO containing the batch update data and target IDs
     * @throws RuntimeException if any related entities are not found
     */
    @Transactional
    public void batchUpdate(CodeGroupBatchUpdateDTO dto) {
        List<CodeGroup> entities = repository.findAllById(dto.getCodeGroupIds());

        if (!entities.isEmpty()) {
            entities.forEach(entity -> {
                if (entity.getParentId() == null || entity.getParentId().trim().isEmpty()) { entity.setParentId(null);}
                if (dto.getIsActive() != null) {
                    entity.setIsActive(dto.getIsActive());
                }

            });

            super.updateBatch(entities);
        }
    }



    /**
     * Retrieves a tree or subtree of CodeGroup entities as a list of CodeGroupListDTO.
     *
     * <ul>
     *   <li>id == null, fullTree == true  : Returns the entire tree (all nodes)</li>
     *   <li>id == null, fullTree == false : Returns only root nodes</li>
     *   <li>id != null, fullTree == true  : Returns the entire subtree rooted at the given id</li>
     *   <li>id != null, fullTree == false : Returns the node itself and its direct children</li>
     * </ul>
     *
     * @param id       The root node id (nullable). If null, operates on the whole tree.
     * @param fullTreeFlag If true, returns the full tree/subtree; if false, returns only roots or direct children.
     * @return List of CodeGroupListDTO representing the requested tree structure
     * @throws EntityNotFoundException if the specified id does not exist
     */
    @Transactional(readOnly = true)
    public List<CodeGroupListDTO> findTree(String id, Boolean fullTreeFlag) {
        boolean fullTree = Boolean.TRUE.equals(fullTreeFlag);
        List<CodeGroup> entities;

        if (id == null || id.trim().isEmpty()) {
            entities = fullTree
                ? super.findCompleteHierarchy()
                : super.findRoots();
        } else {
            CodeGroup self = super.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CodeGroup not found: " + id));

            if (fullTree) {
                entities = super.findWithDescendants(id);
            } else {
                List<CodeGroup> children = super.findDirectChildren(id);
                entities = new ArrayList<>(children.size() + 1);
                entities.add(self);
                entities.addAll(children);
            }
        }

        return entities.stream()
            .map(e -> modelMapper.map(e, CodeGroupListDTO.class))
            .collect(Collectors.toList());
    }


    /**
     * Updates the order of multiple CodeGroup entities.
     *
     * @param orderUpdateDtos orderedChildIds List of DTOs containing the IDs and new order values
     * @return List of CodeGroupDetailDTO of the updated entities
     * @throws RuntimeException if any entity is not found
     */
    @Transactional
    public List<CodeGroupListDTO> updateOrders(String parentId, List<CodeGroupDTOs.CodeGroupOrderUpdateDTO> orderUpdateDtos) {
        // Convert orderUpdateDtos to orderedChildIds maintaining the order
        List<String> orderedChildIds = new ArrayList<>(orderUpdateDtos.stream()
                .sorted((dto1, dto2) -> {
                    Integer order1 = dto1.getSortOrder() != null ? dto1.getSortOrder() : Integer.MAX_VALUE;
                    Integer order2 = dto2.getSortOrder() != null ? dto2.getSortOrder() : Integer.MAX_VALUE;
                    return order1.compareTo(order2);
                })
                .map(CodeGroupDTOs.CodeGroupOrderUpdateDTO::getCodeGroupId)
                .collect(Collectors.toList()));

        super.reorderChildren(parentId, orderedChildIds);

        return super.findDirectChildren(parentId).stream()
                .map(codeGroup -> modelMapper.map(codeGroup, CodeGroupListDTO.class))
                .collect(Collectors.toList());
    }

    
}
    