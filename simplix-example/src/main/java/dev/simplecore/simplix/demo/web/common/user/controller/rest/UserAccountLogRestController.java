package dev.simplecore.simplix.demo.web.common.user.controller.rest;

import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.controller.SimpliXStandardApi;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.demo.web.common.user.dto.UserAccountLogDTOs.*;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserAccountLog;

import dev.simplecore.simplix.demo.domain.common.user.entity.UserAccountLog.UserAccountLogId;
import java.util.stream.Collectors;

import dev.simplecore.simplix.demo.web.common.user.service.UserAccountLogService;
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
 * REST Controller for user account change logs.
 *
 * @author Taehwan Kwag
 * @since 2025-04-27
 */
@RestController
@RequestMapping("/api/user/accountlog")
@Tag(name = "simplix.demo.UserAccountLog", description = "User Account Change Log")
public class UserAccountLogRestController extends SimpliXBaseController<UserAccountLog, UserAccountLogId> {

    private final UserAccountLogService service;

    /**
     * Constructor for UserAccountLogRestController
     *
     * @param service The service instance for handling UserAccountLog operations
     * @generated SimpliX Generator Version 1.0.0 - 2025-04-27T19:16:32.131+09:00
     */
    protected UserAccountLogRestController(UserAccountLogService service) {
        super(service);
        this.service = service;
    }

    /**
     * Creates a new UserAccountLog entity
     *
     * @param createDto The DTO containing the data to create a new UserAccountLog
     * @return ResponseEntity containing the created UserAccountLogDetailDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-04-27T19:16:32.131+09:00
     */
    @PostMapping("/create")
    @Operation(summary = "Create UserAccountLog", description = "Creates a new user account change log")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccountLog', 'create')")
    public ResponseEntity<SimpliXApiResponse<UserAccountLogDetailDTO>> create(
        @RequestBody @Validated UserAccountLogCreateDTO createDto) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.create(createDto)));
    }

    /**
     * Updates an existing UserAccountLog entity
     *
     * @param userId userId
     * @param logTime logTime
     * @param updateDto The DTO containing the updated data
     * @return ResponseEntity containing the updated UserAccountLogDetailDTO or 404 if not found
     * @generated SimpliX Generator Version 1.0.0 - 2025-04-27T19:16:32.131+09:00
     */
    @PutMapping("/{userId}__{logTime}")
    @Operation(summary = "Update UserAccountLog", description = "Updates existing user account change log")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccountLog', 'edit')")
    public ResponseEntity<SimpliXApiResponse<UserAccountLogDetailDTO>> update(
        @PathVariable String userId, @PathVariable String logTime, 
        @RequestBody @Validated UserAccountLogUpdateDTO updateDto) {
        UserAccountLogId id = (new UserAccountLogId()).fromPathVariables(userId, logTime);
        updateDto.setId(id);
        
        return service.findById(id)
                .map(entity -> ResponseEntity.ok(SimpliXApiResponse.success(service.update(entity, updateDto))))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserAccountLog not found")));
    }

    /**
     * Updates multiple UserAccountLog entities in a single operation
     *
     * @param updateDtos Set of DTOs containing the data to update multiple UserAccountLog
     * @return ResponseEntity containing the list of updated UserAccountLogDetailDTOs and success status
     * @throws IllegalArgumentException if any of the updateDtos are invalid
     * @generated SimpliX Generator Version 1.0.0 - 2025-04-27T19:16:32.131+09:00
     */
    @PatchMapping
    @Operation(summary = "Update Multiple UserAccountLog", description = "Updates multiple existing user account change logs")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccountLog', 'edit')")
    public ResponseEntity<SimpliXApiResponse<List<UserAccountLogDetailDTO>>> multiUpdate(
        @RequestBody Set<UserAccountLogUpdateDTO> updateDtos) {
        List<UserAccountLogDetailDTO> updatedItems = service.multiUpdate(updateDtos);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(updatedItems));
    }

    /**
     * Deletes a UserAccountLog by its ID
     *
     * @param userId userId
     * @param logTime logTime
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-04-27T19:16:32.131+09:00
     */
    @DeleteMapping("/{userId}__{logTime}")
    @Operation(summary = "Delete UserAccountLog", description = "Deletes user account change log by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccountLog', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> delete(
        @PathVariable String userId, @PathVariable String logTime) {
        UserAccountLogId id = (new UserAccountLogId()).fromPathVariables(userId, logTime);
        if (!service.existsById(id)) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserAccountLog not found"));
        }
        service.delete(id);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserAccountLog deleted successfully"));
    }

    /**
     * Retrieves a UserAccountLog by its ID
     *
     * @param userId userId
     * @param logTime logTime
     * @return ResponseEntity containing the found UserAccountLogDetailDTO or 404 if not found
     * @generated SimpliX Generator Version 1.0.0 - 2025-04-27T19:16:32.131+09:00
     */
    @GetMapping("/{userId}__{logTime}")
    @Operation(summary = "Get UserAccountLog", description = "Retrieves user account change log by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccountLog', 'view')")
    public ResponseEntity<SimpliXApiResponse<UserAccountLogDetailDTO>> get(
        @PathVariable String userId, @PathVariable String logTime) {
        UserAccountLogId id = (new UserAccountLogId()).fromPathVariables(userId, logTime);
        
        return service.findById(id, UserAccountLogDetailDTO.class)
                .map(result -> ResponseEntity.ok(SimpliXApiResponse.success(result)))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserAccountLog not found")));
    }

    /**
     * Batch updates multiple UserAccountLog entities
     *
     * @param batchUpdateDto The DTO containing the batch update data
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-04-27T19:16:32.131+09:00
     */
    @PatchMapping("/batch")
    @Operation(summary = "Batch Update UserAccountLogs", description = "Updates multiple user account change logs")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccountLog', 'edit')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchUpdate(@RequestBody @Validated UserAccountLogBatchUpdateDTO batchUpdateDto) {
        try {
            service.batchUpdate(batchUpdateDto);
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserAccountLogs updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "Failed to process batch update"));
        }
    }

    /**
     * Deletes multiple UserAccountLog entities by their IDs
     * 
     * @param compositeIds List of composite key strings (format: ["userId__logTime", ...])
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-04-27T19:16:32.131+09:00
     */
    @DeleteMapping("/batch")
    @Operation(summary = "Delete multiple UserAccountLogs", description = "Deletes multiple user account change logs by their IDs")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccountLog', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchDelete(@RequestParam List<String> compositeIds) {
        List<UserAccountLogId> ids = compositeIds.stream()
                .map(compositeId -> (new UserAccountLogId()).fromCompositeId(compositeId))
                .collect(Collectors.toList());
        service.batchDelete(ids);
        
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserAccountLogs deleted successfully"));
    }

    //----------------------------------
    // Searchable JPA
    //----------------------------------

    /**
     * Search UserAccountLog entities with GET method
     *
     * @param params Map of search parameters
     * @return ResponseEntity containing a page of UserAccountLogListDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-04-27T19:16:32.131+09:00
     */
    @GetMapping("/search")
    @Operation(summary = "Search UserAccountLog list (GET)", description = "Searches user account change logs with various conditions using GET method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccountLog', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<UserAccountLogListDTO>>> search(
        @RequestParam(required = false) @SearchableParams(UserAccountLogSearchDTO.class) Map<String, String> params
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(params)));
    }

    /**
     * Search UserAccountLog entities with POST method
     *
     * @param searchCondition The search conditions for filtering UserAccountLogs
     * @return ResponseEntity containing a page of UserAccountLogListDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-04-27T19:16:32.131+09:00
     */
    @PostMapping("/search")
    @Operation(summary = "Search UserAccountLog list (POST)", description = "Searches user account change logs with various conditions using POST method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccountLog', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<UserAccountLogListDTO>>> search(
        @RequestBody @Validated SearchCondition<UserAccountLogSearchDTO> searchCondition
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(searchCondition)));
    }
}
