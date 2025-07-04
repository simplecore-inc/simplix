package dev.simplecore.simplix.demo.web.common.user.controller.rest;

import dev.simplecore.simplix.web.controller.SimpliXBaseController;
import dev.simplecore.simplix.web.controller.SimpliXStandardApi;
import dev.simplecore.simplix.core.model.SimpliXApiResponse;
import dev.simplecore.simplix.demo.permission.CustomUserDetails;
import dev.simplecore.simplix.demo.web.common.user.dto.UserAccountDTOs.*;
import dev.simplecore.simplix.demo.web.common.user.excel.UserAccountListExcel;
import dev.simplecore.simplix.demo.domain.common.user.entity.UserAccount;
import dev.simplecore.simplix.demo.web.common.user.service.UserAccountService;
import dev.simplecore.simplix.excel.api.ExcelExporter;
import dev.simplecore.simplix.excel.impl.exporter.StandardExcelExporter;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.openapi.annotation.SearchableParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;



/**
 * REST Controller for user accounts.
 *
 * @author thkwag
 * @since 2025-02-04
 */
@RestController
@RequestMapping("/api/user/account")
@Tag(name = "common.user.UserAccount", description = "User Account")
public class UserAccountRestController extends SimpliXBaseController<UserAccount, String> {

    private final UserAccountService service;

    /**
     * Constructor for UserAccountRestController
     *
     * @param service The service instance for handling UserAccount operations
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-04T15:58:41.833+09:00
     */
    protected UserAccountRestController(UserAccountService service) {
        super(service);
        this.service = service;
    }


    /**
     * Creates a new UserAccount entity
     *
     * @param createDto The DTO containing the data to create a new UserAccount
     * @return ResponseEntity containing the created UserAccountDetailDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-04T15:58:41.833+09:00
     */
    @PostMapping("/create")
    @Operation(summary = "Create UserAccount", description = "Creates a new user account")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccount', 'create')")
    public ResponseEntity<SimpliXApiResponse<UserAccountDetailDTO>> create(@RequestBody @Validated UserAccountCreateDTO createDto) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.create(createDto)));
    }
    

    /**
     * Updates an existing UserAccount entity
     *
     * @param id The ID of the UserAccount to update
     * @param updateDto The DTO containing the updated data
     * @return ResponseEntity containing the updated UserAccountDetailDTO or 404 if not found
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-04T15:58:41.833+09:00
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update UserAccount", description = "Updates existing user account")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccount', 'edit') or @userAccountService.hasOwnerPermission('edit', #id, #updateDto)")
    public ResponseEntity<SimpliXApiResponse<UserAccountDetailDTO>> update(@PathVariable String id, @RequestBody @Validated UserAccountUpdateDTO updateDto) {
        return service.findById(id)
                .map(entity -> ResponseEntity.ok(SimpliXApiResponse.success(service.update(entity, updateDto))))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserAccount not found")));
    }

     /**
      * Updates multiple UserAccount entities in a single operation
      *
      * @param updateDtos Set of DTOs containing the data to update multiple UserAccount
      * @return ResponseEntity containing the list of updated UserAccountDetailDTOs and success status
      * @throws IllegalArgumentException if any of the updateDtos are invalid
      * @generated SimpliX Generator Version 1.0.0 - 2025-02-04T15:58:41.833+09:00
      */
      @PatchMapping
      @Operation(summary = "Update Multiple UserAccount", description = "Updates multiple existing user accounts")
      @SimpliXStandardApi
      @PreAuthorize("hasPermission('UserAccount', 'edit')")
      public ResponseEntity<SimpliXApiResponse<List<UserAccountDetailDTO>>> multiUpdate(@RequestBody Set<UserAccountUpdateDTO> updateDtos) {
          List<UserAccountDetailDTO> updatedItems = service.multiUpdate(updateDtos);
          return ResponseEntity.ok(SimpliXApiResponse.success(updatedItems));
      }


    /**
     * Deletes a UserAccount by its ID
     *
     * @param id The ID of the UserAccount to delete
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-04T15:58:41.833+09:00
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete UserAccount", description = "Deletes user account by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccount', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> delete(@PathVariable String id) {
        if (!service.existsById(id)) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserAccount not found"));
        }
        service.delete(id);
        return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserAccount deleted successfully"));
    }


    /**
     * Retrieves a UserAccount by its ID
     *
     * @param id The ID of the UserAccount to retrieve
     * @return ResponseEntity containing the found UserAccountDetailDTO or 404 if not found
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-04T15:58:41.833+09:00
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get UserAccount", description = "Retrieves user account by ID")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccount', 'view') or @userAccountService.hasOwnerPermission('view', #id)")
    public ResponseEntity<SimpliXApiResponse<UserAccountDetailDTO>> get(@PathVariable String id) {
        return service.findById(id, UserAccountDetailDTO.class)
                .map(result -> ResponseEntity.ok(SimpliXApiResponse.success(result)))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "UserAccount not found")));
    }


    /**
     * Batch updates multiple UserAccount entities
     *
     * @param batchUpdateDto The DTO containing the batch update data
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-04T15:58:41.833+09:00
     */
    @PatchMapping("/batch")
    @Operation(summary = "Batch Update UserAccounts", description = "Updates multiple user accounts")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccount', 'edit')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchUpdate(@RequestBody @Validated UserAccountBatchUpdateDTO batchUpdateDto) {
        try {
            service.batchUpdate(batchUpdateDto);
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserAccounts updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "Failed to process batch update"));
        }
    }

    /**
     * Deletes multiple UserAccount entities by their IDs
     *
     * @param ids List of IDs of the UserAccount entities to delete
     * @return ResponseEntity with success/failure message
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-04T15:58:41.833+09:00
     */
    @DeleteMapping("/batch")
    @Operation(summary = "Delete multiple UserAccounts", description = "Deletes multiple user accounts by their IDs")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccount', 'delete')")
    public ResponseEntity<SimpliXApiResponse<Void>> batchDelete(@RequestParam List<String> ids) {
        try {
            service.batchDelete(ids);
            return ResponseEntity.ok(SimpliXApiResponse.success(null, "UserAccounts deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(SimpliXApiResponse.failure(null, "Failed to process batch delete"));
        }
    }


    //----------------------------------
    // Searchable JPA
    //----------------------------------

    /**
     * Search UserAccount entities with GET method
     *
     * @param params Map of search parameters
     * @return ResponseEntity containing a page of UserAccountListDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-04T15:58:41.833+09:00
     */
    @GetMapping("/search")
    @Operation(summary = "Search UserAccount list (GET)", description = "Searches user accounts with various conditions using GET method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccount', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<UserAccountListDTO>>> search(
        @RequestParam(required = false) @SearchableParams(UserAccountSearchDTO.class) Map<String, String> params
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(params)));
    }

    /**
     * Search UserAccount entities with POST method
     *
     * @param searchCondition The search conditions for filtering UserAccounts
     * @return ResponseEntity containing a page of UserAccountListDTO
     * @generated SimpliX Generator Version 1.0.0 - 2025-02-04T15:58:41.833+09:00
     */
    @PostMapping("/search")
    @Operation(summary = "Search UserAccount list (POST)", description = "Searches user accounts with various conditions using POST method")
    @SimpliXStandardApi
    @PreAuthorize("hasPermission('UserAccount', 'list')")
    public ResponseEntity<SimpliXApiResponse<Page<UserAccountListDTO>>> search(
        @RequestBody @Validated SearchCondition<UserAccountSearchDTO> searchCondition
    ) {
        return ResponseEntity.ok(SimpliXApiResponse.success(service.search(searchCondition)));
    }


    @GetMapping("/excel")
    @Operation(summary = "Export UserAccount list to Excel", description = "Exports user accounts to Excel file with various conditions using GET method")
    @PreAuthorize("hasPermission('UserAccount', 'list')")
    public void exportExcel(
        @RequestParam(required = false) @SearchableParams(UserAccountSearchDTO.class) Map<String, String> params,
        HttpServletResponse response
    ) throws Exception {
        SearchCondition<UserAccountSearchDTO> searchCondition =
                new SearchableParamsParser<UserAccountSearchDTO>(UserAccountSearchDTO.class).convert(params);
                
        ExcelExporter<UserAccountListExcel> exporter = new StandardExcelExporter<>(UserAccountListExcel.class)
            .streaming(true)
            .pageSize(1000)
            .dataProvider(pageRequest -> {
                searchCondition.setPage(pageRequest.getPageNumber());
                searchCondition.setSize(pageRequest.getPageSize());
                
                List<UserAccountListExcel> excelList = service.searchForExcel(searchCondition);
                return new PageImpl<>(excelList, pageRequest, 
                    service.search(searchCondition).getTotalElements());
            });
        
        exporter.export(null, response, "UserAccount_List.xlsx");
    }

    @PostMapping("/excel")
    @Operation(summary = "Export UserAccount list to Excel (POST)", description = "Exports user accounts to Excel file with various conditions using POST method")
    @PreAuthorize("hasPermission('UserAccount', 'list')")
    public void exportExcelPost(
        @RequestBody @Validated SearchCondition<UserAccountSearchDTO> searchCondition,
        HttpServletResponse response
    ) throws Exception {
        ExcelExporter<UserAccountListExcel> exporter = new StandardExcelExporter<>(UserAccountListExcel.class)
            .streaming(true)
            .pageSize(1000)
            .dataProvider(pageRequest -> {
                searchCondition.setPage(pageRequest.getPageNumber());
                searchCondition.setSize(pageRequest.getPageSize());
                
                List<UserAccountListExcel> excelList = service.searchForExcel(searchCondition);
                return new PageImpl<>(excelList, pageRequest, 
                    service.search(searchCondition).getTotalElements());
            });
        
        exporter.export(null, response, "UserAccount_List.xlsx");
    }


    /**
     * Retrieves the currently logged-in user's account information
     *
     * @return ResponseEntity containing the current user's UserAccountDetailDTO
     */
    @GetMapping("/me")
    @Operation(summary = "Get Current User", description = "Retrieves the currently logged-in user's account information")
    @SimpliXStandardApi
    public ResponseEntity<SimpliXApiResponse<UserAccountDetailDTO>> getCurrentUser() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return service.findById(userDetails.getId(), UserAccountDetailDTO.class)
                .map(result -> ResponseEntity.ok(SimpliXApiResponse.success(result)))
                .orElse(ResponseEntity.ok(SimpliXApiResponse.failure(null, "Current user not found")));
    }

}
