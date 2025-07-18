package dev.simplecore.simplix.demo.web.common.system.service;

import dev.simplecore.simplix.core.tree.repository.SimpliXTreeRepository;
import dev.simplecore.simplix.core.tree.service.SimpliXTreeBaseService;
import dev.simplecore.simplix.demo.domain.common.system.entity.CodeItem;
import dev.simplecore.simplix.demo.web.common.system.dto.CodeItemDTOs.CodeItemBatchUpdateDTO;
import dev.simplecore.simplix.demo.web.common.system.dto.CodeItemDTOs.CodeItemCreateDTO;
import dev.simplecore.simplix.demo.web.common.system.dto.CodeItemDTOs.CodeItemDetailDTO;
import dev.simplecore.simplix.demo.web.common.system.dto.CodeItemDTOs.CodeItemListDTO;
import dev.simplecore.simplix.demo.web.common.system.dto.CodeItemDTOs.CodeItemUpdateDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.modelmapper.ModelMapper;

@Service
@Transactional
public class CodeItemTreeService extends SimpliXTreeBaseService<CodeItem, String> {

    private final ModelMapper modelMapper;

    private final SimpliXTreeRepository<CodeItem, String> repository;

    public CodeItemTreeService(SimpliXTreeRepository<CodeItem, String> simpliXTreeRepository, ModelMapper modelMapper) {
        super(simpliXTreeRepository);
        this.modelMapper = modelMapper;
        this.repository = simpliXTreeRepository;
    }


    /**
     * Creates a new CodeItem entity from the provided DTO.
     *
     * @param createDTO DTO containing the data for the new CodeItem
     * @return CodeItemDetailDTO of the created entity
     * @throws RuntimeException if related entities are not found
     */
    @Transactional
    public CodeItemDetailDTO create(CodeItemCreateDTO createDTO) {
        CodeItem entity = new CodeItem();
        modelMapper.map(createDTO, entity);

        CodeItem savedEntity = super.create(entity);
        return super.findById(savedEntity.getId())
            .map(e -> modelMapper.map(e, CodeItemDetailDTO.class))
            .orElseThrow(() -> new RuntimeException("Failed to retrieve saved entity"));
    }


    /**
     * Updates an existing CodeItem entity with the provided DTO data.
     *
     * @param entity The existing CodeItem entity to update
     * @param updateDto DTO containing the updated data
     * @return CodeItemDetailDTO of the updated entity
     * @throws RuntimeException if related entities are not found
     */
    @Transactional
    public CodeItemDetailDTO update(CodeItem entity, CodeItemUpdateDTO updateDto) {
        modelMapper.map(updateDto, entity);

        CodeItem savedEntity = super.update(entity);
        return super.findById(savedEntity.getId())
            .map(e -> modelMapper.map(e, CodeItemDetailDTO.class))
            .orElseThrow(() -> new RuntimeException("Failed to retrieve saved entity"));
    }


    /**
     * Updates multiple CodeItem entities in a single operation.
     *
     * @param updateDtos Set of DTOs containing update data for multiple CodeItems
     * @return List of CodeItemDetailDTO for the updated entities
     * @throws RuntimeException if any related entities are not found
     */
    @Transactional
    public List<CodeItemDetailDTO> multiUpdate(Set<CodeItemUpdateDTO> updateDtos) {
        List<CodeItemDetailDTO> updatedEntities = new ArrayList<>();

        for (CodeItemUpdateDTO updateDto : updateDtos) {
            if (updateDto.getCodeId() == null) {
                continue;
            }

            Optional<CodeItem> entityOpt = super.findById(updateDto.getCodeId());
            if (entityOpt.isPresent()) {
                CodeItem entity = entityOpt.get();
                CodeItemDetailDTO updatedEntity = update(entity, updateDto);
                updatedEntities.add(updatedEntity);
            }
        }

        return updatedEntities;
    }


    /**
     * Deletes a CodeItem entity by its ID.
     *
     * @param codeId The ID of the CodeItem to delete
     */
    @Transactional
    public void delete(String codeId) {
        super.deleteById(codeId);
    }



    /**
     * Deletes multiple CodeItem entities by their IDs.
     *
     * @param codeIds List of CodeItem IDs to delete
     */
    @Transactional
    public void batchDelete(List<String> codeIds) {
        super.deleteBatch(codeIds);
    }


        /**
     * Performs batch updates on multiple CodeItem entities.
     *
     * @param dto DTO containing the batch update data and target IDs
     * @throws RuntimeException if any related entities are not found
     */
    @Transactional
    public void batchUpdate(CodeItemBatchUpdateDTO dto) {
        List<CodeItem> entities = repository.findAllById(dto.getCodeIds());

        if (!entities.isEmpty()) {
            entities.forEach(entity -> {
                
                if (dto.getIsActive() != null) {
                    entity.setIsActive(dto.getIsActive());
                }
                
            });

            super.updateBatch(entities);
        }
    }



    /**
     * Retrieves a tree or subtree of CodeItem entities as a list of CodeItemListDTO.
     *
     * <ul>
     *   <li>id == null, fullTree == true  : Returns the entire tree (all nodes)</li>
     *   <li>id == null, fullTree == false : Returns only root nodes</li>
     *   <li>id != null, fullTree == true  : Returns the entire subtree rooted at the given id</li>
     *   <li>id != null, fullTree == false : Returns the node itself and its direct children</li>
     * </ul>
     *
     * @param id       The root node id (nullable). If null, operates on the whole tree.
     * @param fullTree If true, returns the full tree/subtree; if false, returns only roots or direct children.
     * @return List of CodeItemListDTO representing the requested tree structure
     * @throws EntityNotFoundException if the specified id does not exist
     */
    @Transactional(readOnly = true)
    public List<CodeItemListDTO> findTree(String id, Boolean fullTreeFlag) {
        boolean fullTree = Boolean.TRUE.equals(fullTreeFlag);
        List<CodeItem> entities;
    
        if (id == null || id.trim().isEmpty()) {
            entities = fullTree
                ? super.findCompleteHierarchy()
                : super.findRoots();
        } else {
            CodeItem self = super.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("CodeItem not found: " + id));
    
            if (fullTree) {
                entities = super.findWithDescendants(id);
            } else {
                List<CodeItem> children = super.findDirectChildren(id);
                entities = new ArrayList<>(children.size() + 1);
                entities.add(self);
                entities.addAll(children);
            }
        }
    
        return entities.stream()
            .map(e -> modelMapper.map(e, CodeItemListDTO.class))
            .collect(Collectors.toList());
    } 
}
    