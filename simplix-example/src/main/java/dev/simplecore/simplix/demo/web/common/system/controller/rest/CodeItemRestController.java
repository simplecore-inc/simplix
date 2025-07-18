package dev.simplecore.simplix.demo.web.common.system.controller.rest;

import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.controller.SimpliXStandardApi;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.demo.domain.common.system.entity.CodeItem;
import dev.simplecore.simplix.demo.web.common.system.dto.CodeItemDTOs.*;
import dev.simplecore.simplix.demo.web.common.system.service.CodeItemService;
import dev.simplecore.simplix.demo.web.common.system.service.CodeItemTreeService;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.openapi.annotation.SearchableParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Code Item REST Controller
 *
 * @author Taehwan Kwag
 * @since 2025-07-16
 */
@RestController
@RequestMapping("/api/code/item")
@Tag(name = "common.system.CodeItem", description = "Code Item")
public class CodeItemRestController extends SimpliXBaseController<CodeItem, String> {

    private final CodeItemService service;

    private final CodeItemTreeService treeService;

    /**
     * Constructor for CodeItemRestController
     *
     * @param service The service instance for handling CodeItem operations
     */
    protected CodeItemRestController(CodeItemService service, CodeItemTreeService codeItemTreeService) {
        super(service);
        this.service = service;
        this.treeService = codeItemTreeService;
    }

    /**
     * Creates a new CodeItem entity
     *
     * @param createDto The DTO containing the data to create a new CodeItem
     * @return ResponseEntity containing the created CodeItemDetailDTO
     */
    @PostMapping("/create")
    @Operation(summary = "Create CodeItem", description = "Creates a new code item")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem', 'create')")
    public ResponseEntity<SimpliXApiResponse<CodeItemDetailDTO>> create(
        @RequestBody @Validated CodeItemCreateDTO createDto) {
        return ResponseEntity.ok(SimpliXApiResponse.success(treeService.create(createDto)));
    }

    /**
     * Updates an existing CodeItem entity
     *
     * @param codeId The ID of the CodeItem to update
     * @param updateDto The DTO containing the updated data
     * @return ResponseEntity containing the updated CodeItemDetailDTO or 404 if not found
     */
    @PutMapping("/{codeId}")
    @Operation(summary = "Update CodeItem", description = "Updates existing code item")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem', 'edit')")
    public ResponseEntity<SimpliXApiResponse<CodeItemDetailDTO>> update(
        @PathVariable String codeId, 
        @RequestBody @Validated CodeItemUpdateDTO updateDto) {

        return service.findById(codeId)
                .map(entity -> ResponseEntity.ok(SimpliXApiResponse.success(treeService.update(entity, updateDto))))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "CodeItem not found")));
    }

    /**
     * Updates multiple CodeItem entities in a single operation
     *
     * @param updateDtos Set of DTOs containing the data to update multiple CodeItem
     * @return ResponseEntity containing the list of updated CodeItemDetailDTOs and success status
     * @throws IllegalArgumentException if any of the updateDtos are invalid
     */
    @PatchMapping
    @Operation(summary = "Update Multiple CodeItem", description = "Updates multiple existing code items")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem', 'edit')")
    public ResponseEntity<SimpliXApiResponse<List<CodeItemDetailDTO>>> multiUpdate(
        @RequestBody Set<CodeItemUpdateDTO> updateDtos) {
        List<CodeItemDetailDTO> updatedItems = treeService.multiUpdate(updateDtos);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(updatedItems));
    }

    /**
     * Deletes a CodeItem by its ID
     *
     * @param codeId The ID of the CodeItem to delete
     * @return ResponseEntity with success/failure message
     */
    @DeleteMapping("/{codeId}")
    @Operation(summary = "Delete CodeItem", description = "Deletes code item by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> delete(
        @PathVariable String codeId) {
        
        if (!service.existsById(codeId)) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "CodeItem not found"));
        }
        treeService.delete(codeId);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "CodeItem deleted successfully"));
    }

    /**
     * Retrieves a CodeItem by its ID
     *
     * @param codeId The ID of the CodeItem to retrieve
     * @return ResponseEntity containing the found CodeItemDetailDTO or 404 if not found
     */
    @GetMapping("/{codeId}")
    @Operation(summary = "Get CodeItem", description = "Retrieves code item by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem', 'view')")
    public ResponseEntity<SimpliXApiResponse<CodeItemDetailDTO>> get(
        @PathVariable String codeId) {

        return service.findById(codeId, CodeItemDetailDTO.class)
                .map(result -> ResponseEntity.ok(SimpliXApiResponse.success(result)))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "CodeItem not found")));
    }

    /**
     * Batch updates multiple CodeItem entities
     *
     * @param batchUpdateDto The DTO containing the batch update data
     * @return ResponseEntity with success/failure message
     */
    @PatchMapping("/batch")
    @Operation(summary = "Batch Update CodeItems", description = "Updates multiple code items")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem', 'edit')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchUpdate(@RequestBody @Validated CodeItemBatchUpdateDTO batchUpdateDto) {
        try {
            treeService.batchUpdate(batchUpdateDto);
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "CodeItems updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "Failed to process batch update"));
        }
    }

    /**
     * Deletes multiple CodeItem entities by their IDs
     * 
     * @param codeIds List of IDs of the CodeItem entities to delete
     * @return ResponseEntity with success/failure message
     */
    @DeleteMapping("/batch")
    @Operation(summary = "Delete multiple CodeItems", description = "Deletes multiple code items by their IDs")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchDelete(@RequestParam List<String> codeIds) {
        treeService.batchDelete(codeIds);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "CodeItems deleted successfully"));
    }

    //----------------------------------
    // Searchable JPA
    //----------------------------------

    /**
     * Search CodeItem entities with GET method
     *
     * @param params Map of search parameters
     * @return ResponseEntity containing a page of CodeItemListDTO
     */
    @GetMapping("/search")
    @Operation(summary = "Search CodeItem list (GET)", description = "Searches code items with various conditions using GET method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<CodeItemListDTO>>> simpleSearch(
        @RequestParam(required = false) @SearchableParams(CodeItemSearchDTO.class) Map<String, String> params
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(params)));
    }

    /**
     * Search CodeItem entities with POST method
     *
     * @param searchCondition The search conditions for filtering CodeItems
     * @return ResponseEntity containing a page of CodeItemListDTO
     */
    @PostMapping("/search")
    @Operation(summary = "Search CodeItem list (POST)", description = "Searches code items with various conditions using POST method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<CodeItemListDTO>>> search(
        @RequestBody @Validated SearchCondition<CodeItemSearchDTO> searchCondition
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(searchCondition)));
    }


    /**
     * Retrieves root nodes or the entire code item tree.
     *
     * <ul>
     *   <li>fullTree=true  : Returns the entire tree (all nodes)</li>
     *   <li>fullTree=false : Returns only root nodes</li>
     * </ul>
     *
     * @param fullTree If true, returns the full tree; if false, returns only root nodes
     * @return ResponseEntity containing a list of CodeItemListDTO representing the requested tree structure
     */
    @GetMapping("/tree")
    @Operation(
      summary     = "Get root nodes",
      description = "fullTree=true → entire tree, false → root nodes only"
    )
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem','list')")
    public ResponseEntity<SimpliXApiResponse<List<CodeItemListDTO>>> getRoots(
        @RequestParam(value = "fullTree", defaultValue = "false") boolean fullTree
    ) {
        List<CodeItemListDTO> result = treeService.findTree(null, fullTree);
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

    /**
     * Retrieves a subtree or direct children of a specific code item node.
     *
     * <ul>
     *   <li>fullTree=true  : Returns the entire subtree rooted at the given codeId</li>
     *   <li>fullTree=false : Returns the node itself and its direct children</li>
     * </ul>
     *
     * @param codeId   The root node id for the subtree
     * @param fullTree If true, returns the full subtree; if false, returns only the node and its direct children
     * @return ResponseEntity containing a list of CodeItemListDTO representing the requested subtree structure
     */
    @GetMapping("/tree/{codeId}")
    @Operation(
      summary     = "Get subtree",
      description = "fullTree=true → entire subtree, false → direct children only"
    )
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('CodeItem','list')")
    public ResponseEntity<SimpliXApiResponse<List<CodeItemListDTO>>> getSubtree(
        @PathVariable String codeId,
        @RequestParam(value = "fullTree", defaultValue = "false") boolean fullTree
    ) {
        List<CodeItemListDTO> result = treeService.findTree(codeId, fullTree);
        return ResponseEntity.ok(SimpliXApiResponse.success(result));
    }

}
