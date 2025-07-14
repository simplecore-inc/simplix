package dev.simplecore.simplix.demo.web.common.user.controller.rest;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.openapi.annotation.SearchableParams;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserOrganization;
import dev.simplecore.simplix.demo.web.common.user.dto.UserOrganizationDTOs.*;
import dev.simplecore.simplix.demo.web.common.user.service.UserOrganizationService;
import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.controller.SimpliXStandardApi;
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
 * REST Controller for user organizations.
 *
 * @author thkwag
 * @since 2025-02-05
 */
@RestController
@RequestMapping("/api/user/organization")
@Tag(name = "common.user.UserOrganization", description = "User Organization")
public class UserOrganizationRestController extends SimpliXBaseController<UserOrganization, String> {

    private final UserOrganizationService service;

    /**
     * Constructor for UserOrganizationRestController
     *
     * @param service The service instance for handling UserOrganization operations
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:17.973+09:00
     */
    protected UserOrganizationRestController(UserOrganizationService service) {
        super(service);
        this.service = service;
    }


    /**
     * Creates a new UserOrganization entity
     *
     * @param createDto The DTO containing the data to create a new UserOrganization
     * @return ResponseEntity containing the created UserOrganizationDetailDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:17.973+09:00
     */
    @PostMapping("/create")
    @Operation(summary = "Create UserOrganization", description = "Creates a new user organization")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'create')")
    public ResponseEntity<SimpliXApiResponse<UserOrganizationDetailDTO>> create(
        @RequestBody @Validated UserOrganizationCreateDTO createDto) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.create(createDto)));
    }
    

    /**
     * Updates an existing UserOrganization entity
     *
     * @param id The ID of the UserOrganization to update
     * @param updateDto The DTO containing the updated data
     * @return ResponseEntity containing the updated UserOrganizationDetailDTO or 404 if not found
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:17.973+09:00
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update UserOrganization", description = "Updates existing user organization")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'edit')")
    public ResponseEntity<SimpliXApiResponse<UserOrganizationDetailDTO>> update(
        @PathVariable String id, @RequestBody @Validated UserOrganizationUpdateDTO updateDto) {
        return service.findById(id)
                .map(entity -> ResponseEntity.ok(SimpliXApiResponse.success(service.update(entity, updateDto))))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserOrganization not found")));
    }


     /**
      * Updates multiple UserOrganization entities in a single operation
      *
      * @param updateDtos Set of DTOs containing the data to update multiple UserOrganization
      * @return ResponseEntity containing the list of updated UserOrganizationDetailDTOs and success status
      * @throws IllegalArgumentException if any of the updateDtos are invalid
      * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:17.973+09:00
      */
      @PatchMapping
      @Operation(summary = "Update Multiple UserOrganization", description = "Updates multiple existing user organizations")
      @SimpliXStandardApi
      @PreAuthorize("hasPermission('UserOrganization', 'edit')")
      public ResponseEntity<SimpliXApiResponse<List<UserOrganizationDetailDTO>>> multiUpdate(
        @RequestBody Set<UserOrganizationUpdateDTO> updateDtos) {
          List<UserOrganizationDetailDTO> updatedItems = service.multiUpdate(updateDtos);
          return ResponseEntity.ok(SimpliXApiResponse.success(updatedItems));
      }


    /**
     * Deletes a UserOrganization by its ID
     *
     * @param id The ID of the UserOrganization to delete
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:17.973+09:00
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete UserOrganization", description = "Deletes user organization by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> delete(@PathVariable String id) {
        if (!service.existsById(id)) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserOrganization not found"));
        }
        service.delete(id);
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserOrganization deleted successfully"));
    }


    /**
     * Retrieves a UserOrganization by its ID
     *
     * @param id The ID of the UserOrganization to retrieve
     * @return ResponseEntity containing the found UserOrganizationDetailDTO or 404 if not found
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:17.973+09:00
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get UserOrganization", description = "Retrieves user organization by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'view')")
    public ResponseEntity<SimpliXApiResponse<UserOrganizationDetailDTO>> get(@PathVariable String id) {
        return service.findById(id, UserOrganizationDetailDTO.class)
                .map(result -> ResponseEntity.ok(SimpliXApiResponse.success(result)))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserOrganization not found")));
    }


    /**
     * Batch updates multiple UserOrganization entities
     *
     * @param batchUpdateDto The DTO containing the batch update data
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:17.973+09:00
     */
    @PatchMapping("/batch")
    @Operation(summary = "Batch Update UserOrganizations", description = "Updates multiple user organizations")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'edit')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchUpdate(@RequestBody @Validated UserOrganizationBatchUpdateDTO batchUpdateDto) {
        try {
            service.batchUpdate(batchUpdateDto);
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserOrganizations updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "Failed to process batch update"));
        }
    }

    /**
     * Deletes multiple UserOrganization entities by their IDs
     *
     * @param ids List of IDs of the UserOrganization entities to delete
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:17.973+09:00
     */
    @DeleteMapping("/batch")
    @Operation(summary = "Delete multiple UserOrganizations", description = "Deletes multiple user organizations by their IDs")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchDelete(@RequestParam List<String> ids) {
        try {
            service.batchDelete(ids);
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserOrganizations deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "Failed to process batch delete"));
        }
    }


    //----------------------------------
    // Searchable JPA
    //----------------------------------

    /**
     * Search UserOrganization entities with GET method
     *
     * @param params Map of search parameters
     * @return ResponseEntity containing a page of UserOrganizationListDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:17.973+09:00
     */
    @GetMapping("/search")
    @Operation(summary = "Search UserOrganization list (GET)", description = "Searches user organizations with various conditions using GET method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<UserOrganizationListDTO>>> simpleSearch(
        @RequestParam(required = false) @SearchableParams(UserOrganizationSearchDTO.class) Map<String, String> params
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(params)));
    }

    /**
     * Search UserOrganization entities with POST method
     *
     * @param searchCondition The search conditions for filtering UserOrganizations
     * @return ResponseEntity containing a page of UserOrganizationListDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:17.973+09:00
     */
    @PostMapping("/search")
    @Operation(summary = "Search UserOrganization list (POST)", description = "Searches user organizations with various conditions using POST method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<UserOrganizationListDTO>>> search(
        @RequestBody @Validated SearchCondition<UserOrganizationSearchDTO> searchCondition
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(searchCondition)));
    }

    //----------------------------------
    // Organization Tree APIs
    //----------------------------------

    @GetMapping("/tree")
    @Operation(summary = "Get Organization Tree", description = "Retrieves the entire organization tree structure")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'list')")
    public ResponseEntity<SimpliXApiResponse<List<UserOrganizationTreeDTO>>> getOrganizationTree() {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.getOrganizationTree()));
    }

    @GetMapping("/tree/{id}")
    @Operation(summary = "Get Organization SubTree", description = "Retrieves a subtree with a specific organization as the root")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'list')")
    public ResponseEntity<SimpliXApiResponse<List<UserOrganizationTreeDTO>>> getOrganizationSubTree(
            @PathVariable String id) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.getOrganizationSubTree(id)));
    }

    @GetMapping("/tree/{id}/children")
    @Operation(summary = "Get Direct Children", description = "Retrieves a list of direct subordinate organizations for a specific organization")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'list')")
    public ResponseEntity<SimpliXApiResponse<List<UserOrganizationMyBatisDTO>>> getDirectChildren(
            @PathVariable String id) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.getDirectChildren(id)));
    }

    @GetMapping("/tree/{id}/parent")
    @Operation(summary = "Get Direct Parent", description = "Retrieves the direct parent organization of a specific organization")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'list')")
    public ResponseEntity<SimpliXApiResponse<UserOrganizationMyBatisDTO>> getDirectParent(
            @PathVariable String id) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.getDirectParent(id)));
    }

    @GetMapping("/tree/{id}/all-children")
    @Operation(summary = "Get All Children IDs", description = "Retrieves a list of all subordinate organization IDs for a specific organization")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'list')")
    public ResponseEntity<SimpliXApiResponse<List<String>>> getAllChildrenIds(
            @PathVariable String id) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.getAllChildrenIds(id)));
    }

    @GetMapping("/tree/{id}/all-parents")
    @Operation(summary = "Get All Parent IDs", description = "Retrieves a list of all parent organization IDs for a specific organization")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'list')")
    public ResponseEntity<SimpliXApiResponse<List<String>>> getAllParentIds(
            @PathVariable String id) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.getAllParentIds(id)));
    }

    @GetMapping("/tree/check-ancestor")
    @Operation(summary = "Check Ancestor Relationship", description = "Checks if a specific organization is an ancestor of another organization")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'list')")
    public ResponseEntity<SimpliXApiResponse<Boolean>> isAncestorOf(
            @RequestParam String ancestorId,
            @RequestParam String descendantId) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.isAncestorOf(ancestorId, descendantId)));
    }

    @GetMapping("/tree/check-descendant")
    @Operation(summary = "Check Descendant Relationship", description = "Checks if a specific organization is a descendant of another organization")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserOrganization', 'list')")
    public ResponseEntity<SimpliXApiResponse<Boolean>> isDescendantOf(
            @RequestParam String descendantId,
            @RequestParam String ancestorId) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.isDescendantOf(descendantId, ancestorId)));
    }

}
