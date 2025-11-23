package com.cloud_ml_app_thesis.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_ml_app_thesis.dto.category.CategoryDTO;
import com.cloud_ml_app_thesis.dto.category.CategoryRequestDTO;
import com.cloud_ml_app_thesis.dto.request.category.CategoryCreateRequest;
import com.cloud_ml_app_thesis.dto.request.category.CategoryRejectRequest;
import com.cloud_ml_app_thesis.dto.request.category.CategoryUpdateRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.service.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Tag(name = "Category Management", description = "Endpoints for managing model categories")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Get all categories", description = "Returns all non-deleted categories")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categories retrieved successfully")
    })
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CategoryDTO>> getAllCategories() {
        List<CategoryDTO> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(categories);
    }

    @Operation(summary = "Get category by ID", description = "Returns a single category by its ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Category not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CategoryDTO> getCategoryById(
            @Parameter(description = "ID of the category to retrieve") @PathVariable @Positive Integer id) {
        CategoryDTO category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(category);
    }

    @Operation(summary = "Get pending category requests (Admin only)", description = "Returns all pending category requests for admin approval")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending requests retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only")
    })
    @GetMapping("/requests/pending")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> getPendingCategoryRequests(@AuthenticationPrincipal AccountDetails accountDetails) {
        boolean isAdminOrManager = accountDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN")
                               || auth.getAuthority().equals("ROLE_ADMIN")
                               || auth.getAuthority().equals("CATEGORY_MANAGER")
                               || auth.getAuthority().equals("ROLE_CATEGORY_MANAGER"));

        if (!isAdminOrManager) {
            throw new AccessDeniedException("Admin or Category Manager access required");
        }

        GenericResponse<?> response = categoryService.getPendingCategoryRequests();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all category requests (Admin only)", description = "Returns all category requests with their status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Requests retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only")
    })
    @GetMapping("/requests/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> getAllCategoryRequests(@AuthenticationPrincipal AccountDetails accountDetails) {
        boolean isAdminOrManager = accountDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN")
                               || auth.getAuthority().equals("ROLE_ADMIN")
                               || auth.getAuthority().equals("CATEGORY_MANAGER")
                               || auth.getAuthority().equals("ROLE_CATEGORY_MANAGER"));

        if (!isAdminOrManager) {
            throw new AccessDeniedException("Admin or Category Manager access required");
        }

        GenericResponse<?> response = categoryService.getAllCategoryRequests();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a category", description = "Deletes a category by ID. Only accessible by ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Category deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @DeleteMapping("/{id}/delete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteCategory(
            @AuthenticationPrincipal AccountDetails accountDetails,
            @Parameter(description = "ID of the category to delete") @PathVariable @Positive Integer id) {

        boolean isAdmin = accountDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN") || auth.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new AccessDeniedException("Admin access required");
        }

        String username = accountDetails.getUsername();
        categoryService.deleteCategory(username, id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Operation(summary = "Submit a new category request")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Category request submitted",
                    content = @Content(schema = @Schema(implementation = CategoryRequestDTO.class))
            )
    })
    
    @PostMapping("/addCategory")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> createCategory(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CategoryCreateRequest request) {
        String username = userDetails.getUsername();
       GenericResponse<?> createCategoryRequest = categoryService.createCategory(username, request);
        return ResponseEntity.ok().body(createCategoryRequest);
    }

    @Operation(summary = "Approve a pending category request")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category approved",
                    content = @Content(schema = @Schema(implementation = CategoryRequestDTO.class)))
    })
    @PostMapping("{requestId}/approve")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> approveCategoryRequest(
            @AuthenticationPrincipal AccountDetails accountDetails,
            @Parameter(description = "Request ID to approve") @PathVariable @Positive Integer requestId) {

        boolean isAdminOrManager = accountDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN")
                               || auth.getAuthority().equals("ROLE_ADMIN")
                               || auth.getAuthority().equals("CATEGORY_MANAGER")
                               || auth.getAuthority().equals("ROLE_CATEGORY_MANAGER"));

        if (!isAdminOrManager) {
            throw new AccessDeniedException("Admin or Category Manager access required");
        }

        String username = accountDetails.getUsername();
        GenericResponse<?> approvedRequest = categoryService.approveCategoryRequest(username, requestId);
        return ResponseEntity.ok().body(approvedRequest);
    }

    @Operation(summary = "Reject a pending category request")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Category rejected",
                    content = @Content(schema = @Schema(implementation = CategoryRequestDTO.class)))
    })
    @PatchMapping("{requestId}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> rejectCategoryRequest(
            @AuthenticationPrincipal AccountDetails accountDetails,
            @Parameter(description = "Request ID to reject") @PathVariable @Positive Integer requestId,
            @RequestBody CategoryRejectRequest request) {

        boolean isAdminOrManager = accountDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN")
                               || auth.getAuthority().equals("ROLE_ADMIN")
                               || auth.getAuthority().equals("CATEGORY_MANAGER")
                               || auth.getAuthority().equals("ROLE_CATEGORY_MANAGER"));

        if (!isAdminOrManager) {
            throw new AccessDeniedException("Admin or Category Manager access required");
        }

        String username = accountDetails.getUsername();
        GenericResponse<CategoryRequestDTO> response = categoryService.rejectCategoryRequest(username, requestId, request.getRejectionReason());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Update an existing category")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category updated",
                    content = @Content(schema = @Schema(implementation = CategoryDTO.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden to modify ID if not admin")
    })
    @PatchMapping("/{id}/update")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<CategoryDTO>> updateCategory(
            @AuthenticationPrincipal AccountDetails accountDetails,
            @Parameter(description = "ID of the category to update") @PathVariable @Positive Integer id,
            @Valid @RequestBody CategoryUpdateRequest request) {

        boolean isAdminOrManager = accountDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN")
                               || auth.getAuthority().equals("ROLE_ADMIN")
                               || auth.getAuthority().equals("CATEGORY_MANAGER")
                               || auth.getAuthority().equals("ROLE_CATEGORY_MANAGER"));

        if (!isAdminOrManager) {
            throw new AccessDeniedException("Admin or Category Manager access required");
        }

        String username = accountDetails.getUsername();
        GenericResponse<CategoryDTO> response = categoryService.updateCategory(username, id, request);
        return ResponseEntity.ok(response);
    }
}
