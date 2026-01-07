package com.cloud_ml_app_thesis.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.user.DeleteUserRequest;
import com.cloud_ml_app_thesis.dto.request.user.UserUpdateRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "User Management", description = "Endpoints for managing users")
@RestController
@Slf4j
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "Get current user profile", description = "Returns the authenticated user's profile information")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> getCurrentUser(@AuthenticationPrincipal AccountDetails userDetails) {
        return ResponseEntity.ok(userService.getUserProfile(userDetails.getUser()));
    }

    @Operation(summary = "List all users (Admin only)", description = "Returns a list of all registered users")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only")
    })
    @GetMapping("/all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> getAllUsers(@AuthenticationPrincipal AccountDetails accountDetails) {
        log.info("🔐 getAllUsers called by user: {}, authorities: {}",
                accountDetails.getUsername(),
                accountDetails.getAuthorities());

        boolean isAdmin = accountDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN"));

        if (!isAdmin) {
            log.warn("⛔ Access denied: User {} is not admin", accountDetails.getUsername());
            throw new AccessDeniedException("Admin access required");
        }

        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Get user by username (Admin only)", description = "Returns detailed user information including statistics")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User details retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only")
    })
    @GetMapping("/details/{username}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<GenericResponse<?>> getUserDetails(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserDetails(username));
    }

    @Operation(summary = "Update user info", description = "Allows a user to update profile details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PutMapping("/update")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> updateUser(@AuthenticationPrincipal AccountDetails userDetails, @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUser(userDetails.getUser(), request));
    }

    @Operation(summary = "Update user info by Admin", description = "Allows an admin to update user's profile details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PutMapping("/updateByAdmin/{username}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<GenericResponse<?>> updateUserByAdmin(@PathVariable String username, @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUserByAdmin(username, request));
    }

    @Operation(summary = "Delete own account", description = "Allows a user to delete their account by providing a reason")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized")
    })
    @PatchMapping("/delete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> deleteOwnAccount(
            @AuthenticationPrincipal AccountDetails userDetails,
            @Valid @RequestBody DeleteUserRequest request) {
        userService.deleteUser(userDetails.getUser(), request.getReason());
        return ResponseEntity.ok(new GenericResponse<>(userDetails.getUser() + " has been deleted ", null, "DELETED" , new Metadata()));
    }

    @Operation(summary = "Delete user by admin", description = "Allows an admin to delete any user by providing a reason")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User deleted successfully"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "403", description = "Unauthorized")
    })
    @PatchMapping("/delete/{username}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<GenericResponse<?>> deleteUserByAdmin(
            @PathVariable String username,
            @Valid @RequestBody DeleteUserRequest request) {
        userService.deleteUserByAdmin(username, request.getReason());
        return ResponseEntity.ok(new GenericResponse<>("User deleted successfully", null, "DELETED", new Metadata()));
    }
}
