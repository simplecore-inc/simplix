package dev.simplecore.simplix.demo.web.common.auth.controller.rest;

import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.controller.SimpliXStandardApi;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.demo.web.common.auth.dto.AuthRolePermissionDTOs.*;
import dev.simplecore.simplix.demo.domain.common.auth.entity.AuthRolePermission;
import dev.simplecore.simplix.demo.web.common.auth.service.AuthRolePermissionService;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.openapi.annotation.SearchableParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * REST Controller for managing role-based permission settings.
 *
 * @author thkwag
 * @since 2025-02-05
 */
@RestController
@RequestMapping("/api/auth-role-permission")
@Tag(name = "common.auth.AuthRolePermission", description = "Role-based Permission Settings")
public class AuthRolePermissionRestController extends SimpliXBaseController<AuthRolePermission, String> {

    private final AuthRolePermissionService service;

    /**
     * Constructor for AuthRolePermissionRestController
     *
     * @param service The service instance for handling AuthRolePermission operations
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T18:46:02.603+09:00
     */
    protected AuthRolePermissionRestController(AuthRolePermissionService service) {
        super(service);
        this.service = service;
    }


    /**
     * Creates a new AuthRolePermission entity
     *
     * @param createDto The DTO containing the data to create a new AuthRolePermission
     * @return ResponseEntity containing the created AuthRolePermissionDetailDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T18:46:02.603+09:00
     */
    @PostMapping("/create")
    @Operation(summary = "Create AuthRolePermission", description = "Creates a new role-based permission setting")
    @SimpliXStandardApi
    public ResponseEntity<SimpliXApiResponse<AuthRolePermissionDetailDTO>> create(@RequestBody @Validated AuthRolePermissionUpdateDTO createDto) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.create(createDto)));
    }
    

    /**
     * Updates an existing AuthRolePermission entity
     *
     * @param id The ID of the AuthRolePermission to update
     * @param updateDto The DTO containing the updated data
     * @return ResponseEntity containing the updated AuthRolePermissionDetailDTO or 404 if not found
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T18:46:02.603+09:00
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update AuthRolePermission", description = "Updates existing role-based permission setting")
    @SimpliXStandardApi
    public ResponseEntity<SimpliXApiResponse<AuthRolePermissionDetailDTO>> update(@PathVariable String id, @RequestBody @Validated AuthRolePermissionUpdateDTO updateDto) {
        return service.findById(id)
                .map(entity -> ResponseEntity.ok(SimpliXApiResponse.success(service.update(entity, updateDto))))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "AuthRolePermission not found")));
    }


     /**
      * Updates multiple AuthRolePermission entities in a single operation
      *
      * @param updateDtos Set of DTOs containing the data to update multiple AuthRolePermission
      * @return ResponseEntity containing the list of updated AuthRolePermissionDetailDTOs and success status
      * @throws IllegalArgumentException if any of the updateDtos are invalid
      * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T18:46:02.603+09:00
      */
      @PatchMapping
      @Operation(summary = "Update Multiple AuthRolePermission", description = "Updates multiple existing role-based permission settings")
      @SimpliXStandardApi
      public ResponseEntity<SimpliXApiResponse<List<AuthRolePermissionDetailDTO>>> multiUpdate(@RequestBody Set<AuthRolePermissionUpdateDTO> updateDtos) {
          List<AuthRolePermissionDetailDTO> updatedItems = service.multiUpdate(updateDtos);
          return ResponseEntity.ok(SimpliXApiResponse.success(updatedItems));
      }


    /**
     * Deletes a AuthRolePermission by its ID
     *
     * @param id The ID of the AuthRolePermission to delete
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T18:46:02.603+09:00
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete AuthRolePermission", description = "Deletes role-based permission setting by ID")
    @SimpliXStandardApi
    public ResponseEntity<SimpliXApiResponse<Void>> delete(@PathVariable String id) {
        if (!service.existsById(id)) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "AuthRolePermission not found"));
        }
        service.delete(id);
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "AuthRolePermission deleted successfully"));
    }


    /**
     * Retrieves a AuthRolePermission by its ID
     *
     * @param id The ID of the AuthRolePermission to retrieve
     * @return ResponseEntity containing the found AuthRolePermissionDetailDTO or 404 if not found
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T18:46:02.603+09:00
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get AuthRolePermission", description = "Retrieves role-based permission setting by ID")
    @SimpliXStandardApi
    public ResponseEntity<SimpliXApiResponse<AuthRolePermissionDetailDTO>> get(@PathVariable String id) {
        return service.findById(id, AuthRolePermissionDetailDTO.class)
                .map(result -> ResponseEntity.ok(SimpliXApiResponse.success(result)))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "AuthRolePermission not found")));
    }


    /**
     * Batch updates multiple AuthRolePermission entities
     *
     * @param batchUpdateDto The DTO containing the batch update data
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T18:46:02.603+09:00
     */
    @PatchMapping("/batch")
    @Operation(summary = "Batch Update AuthRolePermissions", description = "Updates multiple role-based permission settings")
    @SimpliXStandardApi
    public ResponseEntity<SimpliXApiResponse<Void>> batchUpdate(@RequestBody @Validated AuthRolePermissionBatchUpdateDTO batchUpdateDto) {
        try {
            service.batchUpdate(batchUpdateDto);
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "AuthRolePermissions updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "Failed to process batch update"));
        }
    }

    /**
     * Deletes multiple AuthRolePermission entities by their IDs
     *
     * @param ids List of IDs of the AuthRolePermission entities to delete
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T18:46:02.603+09:00
     */
    @DeleteMapping("/batch")
    @Operation(summary = "Delete multiple AuthRolePermissions", description = "Deletes multiple role-based permission settings by their IDs")
    @SimpliXStandardApi
    public ResponseEntity<SimpliXApiResponse<Void>> batchDelete(@RequestParam List<String> ids) {
        try {
            service.batchDelete(ids);
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "AuthRolePermissions deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "Failed to process batch delete"));
        }
    }


    //----------------------------------
    // Searchable JPA
    //----------------------------------

    /**
     * Search AuthRolePermission entities with GET method
     *
     * @param params Map of search parameters
     * @return ResponseEntity containing a page of AuthRolePermissionListDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T18:46:02.603+09:00
     */
    @GetMapping("/search")
    @Operation(summary = "Search AuthRolePermission list (GET)", description = "Searches role-based permission settings with various conditions using GET method")
    @SimpliXStandardApi
    public ResponseEntity<SimpliXApiResponse<Page<AuthRolePermissionListDTO>>> simpleSearch(
        @RequestParam(required = false) @SearchableParams(AuthRolePermissionSearchDTO.class) Map<String, String> params
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(params)));
    }

    /**
     * Search AuthRolePermission entities with POST method
     *
     * @param searchCondition The search conditions for filtering AuthRolePermissions
     * @return ResponseEntity containing a page of AuthRolePermissionListDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T18:46:02.603+09:00
     */
    @PostMapping("/search")
    @Operation(summary = "Search AuthRolePermission list (POST)", description = "Searches role-based permission settings with various conditions using POST method")
    @SimpliXStandardApi
    public ResponseEntity<SimpliXApiResponse<Page<AuthRolePermissionListDTO>>> search(
        @RequestBody @Validated SearchCondition<AuthRolePermissionSearchDTO> searchCondition
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(searchCondition)));
    }
}
