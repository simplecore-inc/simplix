package dev.simplecore.simplix.demo.web.common.user.controller.rest;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.openapi.annotation.SearchableParams;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserPosition;
import dev.simplecore.simplix.demo.web.common.user.dto.UserPositionDTOs.*;
import dev.simplecore.simplix.demo.web.common.user.service.UserPositionService;
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
 * REST Controller for user positions.
 *
 * @author thkwag
 * @since 2025-02-05
 */
@RestController
@RequestMapping("/api/user/position")
@Tag(name = "common.user.UserPosition", description = "User Position")
public class UserPositionRestController extends SimpliXBaseController<UserPosition, String> {

    private final UserPositionService service;

    /**
     * Constructor for UserPositionRestController
     *
     * @param service The service instance for handling UserPosition operations
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:03.028+09:00
     */
    protected UserPositionRestController(UserPositionService service) {
        super(service);
        this.service = service;
    }


    /**
     * Creates a new UserPosition entity
     *
     * @param createDto The DTO containing the data to create a new UserPosition
     * @return ResponseEntity containing the created UserPositionDetailDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:03.028+09:00
     */
    @PostMapping("/create")
    @Operation(summary = "Create UserPosition", description = "Creates a new user position")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserPosition', 'create')")
    public ResponseEntity<SimpliXApiResponse<UserPositionDetailDTO>> create(
        @RequestBody @Validated UserPositionCreateDTO createDto) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.create(createDto)));
    }
    

    /**
     * Updates an existing UserPosition entity
     *
     * @param id The ID of the UserPosition to update
     * @param updateDto The DTO containing the updated data
     * @return ResponseEntity containing the updated UserPositionDetailDTO or 404 if not found
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:03.028+09:00
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update UserPosition", description = "Updates existing user position")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserPosition', 'edit')")
    public ResponseEntity<SimpliXApiResponse<UserPositionDetailDTO>> update(
        @PathVariable String id, @RequestBody @Validated UserPositionUpdateDTO updateDto) {
        return service.findById(id)
                .map(entity -> ResponseEntity.ok(SimpliXApiResponse.success(service.update(entity, updateDto))))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserPosition not found")));
    }


     /**
      * Updates multiple UserPosition entities in a single operation
      *
      * @param updateDtos Set of DTOs containing the data to update multiple UserPosition
      * @return ResponseEntity containing the list of updated UserPositionDetailDTOs and success status
      * @throws IllegalArgumentException if any of the updateDtos are invalid
      * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:03.028+09:00
      */
      @PatchMapping
      @Operation(summary = "Update Multiple UserPosition", description = "Updates multiple existing user positions")
      @SimpliXStandardApi
      @PreAuthorize("hasPermission('UserPosition', 'edit')")
      public ResponseEntity<SimpliXApiResponse<List<UserPositionDetailDTO>>> multiUpdate(
        @RequestBody Set<UserPositionUpdateDTO> updateDtos) {
          List<UserPositionDetailDTO> updatedItems = service.multiUpdate(updateDtos);
          return ResponseEntity.ok(SimpliXApiResponse.success(updatedItems));
      }


    /**
     * Deletes a UserPosition by its ID
     *
     * @param id The ID of the UserPosition to delete
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:03.028+09:00
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete UserPosition", description = "Deletes user position by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserPosition', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> delete(@PathVariable String id) {
        if (!service.existsById(id)) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserPosition not found"));
        }
        service.delete(id);
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserPosition deleted successfully"));
    }


    /**
     * Retrieves a UserPosition by its ID
     *
     * @param id The ID of the UserPosition to retrieve
     * @return ResponseEntity containing the found UserPositionDetailDTO or 404 if not found
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:03.028+09:00
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get UserPosition", description = "Retrieves user position by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserPosition', 'view')")
    public ResponseEntity<SimpliXApiResponse<UserPositionDetailDTO>> get(@PathVariable String id) {
        return service.findById(id, UserPositionDetailDTO.class)
                .map(result -> ResponseEntity.ok(SimpliXApiResponse.success(result)))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserPosition not found")));
    }


    /**
     * Batch updates multiple UserPosition entities
     *
     * @param batchUpdateDto The DTO containing the batch update data
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:03.028+09:00
     */
    @PatchMapping("/batch")
    @Operation(summary = "Batch Update UserPositions", description = "Updates multiple user positions")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserPosition', 'edit')")
    public ResponseEntity<SimpliXApiResponse<String>> batchUpdate(@RequestBody @Validated UserPositionBatchUpdateDTO batchUpdateDto) {
        try {
            service.batchUpdate(batchUpdateDto);
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserPositions updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "Failed to process batch update"));
        }
    }

    /**
     * Deletes multiple UserPosition entities by their IDs
     *
     * @param ids List of IDs of the UserPosition entities to delete
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:03.028+09:00
     */
    @DeleteMapping("/batch")
    @Operation(summary = "Delete multiple UserPositions", description = "Deletes multiple user positions by their IDs")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserPosition', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchDelete(@RequestParam List<String> ids) {
        service.batchDelete(ids);
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserPositions deleted successfully"));
    }


    //----------------------------------
    // Searchable JPA
    //----------------------------------

    /**
     * Search UserPosition entities with GET method
     *
     * @param params Map of search parameters
     * @return ResponseEntity containing a page of UserPositionListDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:03.028+09:00
     */
    @GetMapping("/search")
    @Operation(summary = "Search UserPosition list (GET)", description = "Searches user positions with various conditions using GET method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserPosition', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<UserPositionListDTO>>> simpleSearch(
        @RequestParam(required = false) @SearchableParams(UserPositionSearchDTO.class) Map<String, String> params
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(params)));
    }

    /**
     * Search UserPosition entities with POST method
     *
     * @param searchCondition The search conditions for filtering UserPositions
     * @return ResponseEntity containing a page of UserPositionListDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-05T14:39:03.028+09:00
     */
    @PostMapping("/search")
    @Operation(summary = "Search UserPosition list (POST)", description = "Searches user positions with various conditions using POST method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserPosition', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<UserPositionListDTO>>> search(
        @RequestBody @Validated SearchCondition<UserPositionSearchDTO> searchCondition
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(searchCondition)));
    }
}
