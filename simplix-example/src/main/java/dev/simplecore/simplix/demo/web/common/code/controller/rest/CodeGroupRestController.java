package dev.simplecore.simplix.demo.web.common.code.controller.rest;

import dev.simplecore.simplix.demo.domain.common.code.entity.CodeGroup;
import dev.simplecore.simplix.demo.web.common.code.service.CodeGroupService;
import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.controller.SimpliXStandardApi;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.demo.web.common.code.dto.CodeGroupDTOs.*;
import dev.simplecore.simplix.demo.web.common.code.service.CodeGroupTreeService;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.openapi.annotation.SearchableParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Code Group REST Controller
 *
 * @author Taehwan Kwag
 * @since 2025-07-16
 */
@RestController
@RequestMapping("/api/code/group")
@Tag(name = "common.code.CodeGroup", description = "Code Group")
public class CodeGroupRestController extends SimpliXBaseController<CodeGroup, String> {

    private final CodeGroupService service;

    private final CodeGroupTreeService treeService;

    private static final Logger logger = LoggerFactory.getLogger(CodeGroupRestController.class);

    /**
     * Constructor for CodeGroupRestController
     *
     * @param service The service instance for handling CodeGroup operations
     */
    protected CodeGroupRestController(CodeGroupService service, CodeGroupTreeService codeGroupTreeService) {
        super(service);
        this.service = service;
        this.treeService = codeGroupTreeService;
    }

    /**
     * Creates a new CodeGroup entity
     *
     * @param createDto The DTO containing the data to create a new CodeGroup
     * @return ResponseEntity containing the created CodeGroupDetailDTO
     */
    @PostMapping("/create")
    @Operation(summary = "Create CodeGroup", description = "Creates a new code group")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup', 'create')")
    public ResponseEntity<SimpliXApiResponse<CodeGroupDetailDTO>> create(
        @RequestBody @Validated CodeGroupCreateDTO createDto) {
        return ResponseEntity.ok(SimpliXApiResponse.success(treeService.create(createDto)));
    }

    /**
     * Updates an existing CodeGroup entity
     *
     * @param codeGroupId The ID of the CodeGroup to update
     * @param updateDto The DTO containing the updated data
     * @return ResponseEntity containing the updated CodeGroupDetailDTO or 404 if not found
     */
    @PutMapping("/{codeGroupId}")
    @Operation(summary = "Update CodeGroup", description = "Updates existing code group")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup', 'edit')")
    public ResponseEntity<SimpliXApiResponse<CodeGroupDetailDTO>> update(
        @PathVariable String codeGroupId, 
        @RequestBody @Validated CodeGroupUpdateDTO updateDto) {

        return service.findById(codeGroupId)
                .map(entity -> ResponseEntity.ok(SimpliXApiResponse.success(treeService.update(entity, updateDto))))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "CodeGroup not found")));
    }

    /**
     * Updates multiple CodeGroup entities in a single operation
     *
     * @param updateDtos Set of DTOs containing the data to update multiple CodeGroup
     * @return ResponseEntity containing the list of updated CodeGroupDetailDTOs and success status
     * @throws IllegalArgumentException if any of the updateDtos are invalid
     */
    @PatchMapping
    @Operation(summary = "Update Multiple CodeGroup", description = "Updates multiple existing code groups")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup', 'edit')")
    public ResponseEntity<SimpliXApiResponse<List<CodeGroupDetailDTO>>> multiUpdate(
        @RequestBody Set<CodeGroupUpdateDTO> updateDtos) {
        List<CodeGroupDetailDTO> updatedItems = treeService.multiUpdate(updateDtos);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(updatedItems));
    }


    /**
     * Updates the order of multiple CodeGroup entities
     *
     * @param orderUpdateDtos List of DTOs containing the IDs and new order values
     * @return ResponseEntity containing the list of updated CodeGroupDetailDTOs
     */
    @PatchMapping("/order")
    @Operation(summary = "Update CodeGroup Orders", description = "Updates the order of multiple user roles")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup', 'edit')")
    public ResponseEntity<SimpliXApiResponse<List<CodeGroupListDTO>>> updateTreeOrder(
        @RequestParam String parentId,
        @RequestBody @Validated List<CodeGroupOrderUpdateDTO> orderUpdateDtos) {
        try {
            List<CodeGroupListDTO> updatedRoles = treeService.updateOrders(parentId, orderUpdateDtos);
            return ResponseEntity.ok(SimpliXApiResponse.success(updatedRoles, "CodeGroup orders updated successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, e.getMessage()));
        }
    }


    /**
     * Deletes a CodeGroup by its ID
     *
     * @param codeGroupId The ID of the CodeGroup to delete
     * @return ResponseEntity with success/failure message
     */
    @DeleteMapping("/{codeGroupId}")
    @Operation(summary = "Delete CodeGroup", description = "Deletes code group by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> delete(
        @PathVariable String codeGroupId) {
        
        if (!service.existsById(codeGroupId)) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "CodeGroup not found"));
        }
        treeService.delete(codeGroupId);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "CodeGroup deleted successfully"));
    }

    /**
     * Retrieves a CodeGroup by its ID
     *
     * @param codeGroupId The ID of the CodeGroup to retrieve
     * @return ResponseEntity containing the found CodeGroupDetailDTO or 404 if not found
     */
    @GetMapping("/{codeGroupId}")
    @Operation(summary = "Get CodeGroup", description = "Retrieves code group by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup', 'view')")
    public ResponseEntity<SimpliXApiResponse<CodeGroupDetailDTO>> get(
        @PathVariable String codeGroupId) {

        return service.findById(codeGroupId, CodeGroupDetailDTO.class)
                .map(result -> ResponseEntity.ok(SimpliXApiResponse.success(result)))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "CodeGroup not found")));
    }

    /**
     * Batch updates multiple CodeGroup entities
     *
     * @param batchUpdateDto The DTO containing the batch update data
     * @return ResponseEntity with success/failure message
     */
    @PatchMapping("/batch")
    @Operation(summary = "Batch Update CodeGroups", description = "Updates multiple code groups")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup', 'edit')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchUpdate(@RequestBody @Validated CodeGroupBatchUpdateDTO batchUpdateDto) {
        try {
            treeService.batchUpdate(batchUpdateDto);
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "CodeGroups updated successfully"));
        } catch (Exception e) {
            logger.error("Failed to process batch update", e);
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "Failed to process batch update"));
        }
    }

    /**
     * Deletes multiple CodeGroup entities by their IDs
     * 
     * @param codeGroupIds List of IDs of the CodeGroup entities to delete
     * @return ResponseEntity with success/failure message
     */
    @DeleteMapping("/batch")
    @Operation(summary = "Delete multiple CodeGroups", description = "Deletes multiple code groups by their IDs")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchDelete(@RequestParam List<String> codeGroupIds) {
        treeService.batchDelete(codeGroupIds);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "CodeGroups deleted successfully"));
    }

    //----------------------------------
    // Searchable JPA
    //----------------------------------

    /**
     * Search CodeGroup entities with GET method
     *
     * @param params Map of search parameters
     * @return ResponseEntity containing a page of CodeGroupListDTO
     */
    @GetMapping("/search")
    @Operation(summary = "Search CodeGroup list (GET)", description = "Searches code groups with various conditions using GET method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<CodeGroupListDTO>>> simpleSearch(
        @RequestParam(required = false) @SearchableParams(CodeGroupSearchDTO.class) Map<String, String> params
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(params)));
    }

    /**
     * Search CodeGroup entities with POST method
     *
     * @param searchCondition The search conditions for filtering CodeGroups
     * @return ResponseEntity containing a page of CodeGroupListDTO
     */
    @PostMapping("/search")
    @Operation(summary = "Search CodeGroup list (POST)", description = "Searches code groups with various conditions using POST method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<CodeGroupListDTO>>> search(
        @RequestBody @Validated SearchCondition<CodeGroupSearchDTO> searchCondition
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(searchCondition)));
    }


    /**
     * Retrieves root nodes or the entire code group tree.
     *
     * <ul>
     *   <li>fullTree=true  : Returns the entire tree (all nodes)</li>
     *   <li>fullTree=false : Returns only root nodes</li>
     * </ul>
     *
     * @param fullTree If true, returns the full tree; if false, returns only root nodes
     * @return ResponseEntity containing a list of CodeGroupListDTO representing the requested tree structure
     */
    @GetMapping("/tree")
    @Operation(
      summary     = "Get root nodes",
      description = "fullTree=true → entire tree, false → root nodes only"
    )
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup','list')")
    public ResponseEntity<SimpliXApiResponse<List<CodeGroupListDTO>>> getRoots(
        @RequestParam(value = "fullTree", defaultValue = "false") boolean fullTree
    ) {
        List<CodeGroupListDTO> result = treeService.findTree(null, fullTree);
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    /**
     * Retrieves a subtree or direct children of a specific code group node.
     *
     * <ul>
     *   <li>fullTree=true  : Returns the entire subtree rooted at the given codeGroupId</li>
     *   <li>fullTree=false : Returns the node itself and its direct children</li>
     * </ul>
     *
     * @param codeGroupId   The root node id for the subtree
     * @param fullTree If true, returns the full subtree; if false, returns only the node and its direct children
     * @return ResponseEntity containing a list of CodeGroupListDTO representing the requested subtree structure
     */
    @GetMapping("/tree/{codeGroupId}")
    @Operation(
      summary     = "Get subtree",
      description = "fullTree=true → entire subtree, false → direct children only"
    )
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeGroup','list')")
    public ResponseEntity<SimpliXApiResponse<List<CodeGroupListDTO>>> getSubtree(
        @PathVariable String codeGroupId,
        @RequestParam(value = "fullTree", defaultValue = "false") boolean fullTree
    ) {
        List<CodeGroupListDTO> result = treeService.findTree(codeGroupId, fullTree);
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

}
