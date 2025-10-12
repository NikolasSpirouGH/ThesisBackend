package com.cloud_ml_app_thesis.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/users")

public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "Get all users", description = "Allows an admin to retrieve all users")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - admin access required")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericResponse<?>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Get user by username", description = "Allows users to get their own info or admins to get any user's info")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{username}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<GenericResponse<?>> getUserByUsername(
            @PathVariable String username,
            @AuthenticationPrincipal AccountDetails userDetails) {
        return ResponseEntity.ok(userService.getUserByUsername(username, userDetails.getUser()));
    }

    @Operation(summary = "Update user info", description = "Allows a user to update profile details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    @PutMapping("/update")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericResponse<?>> updateUserByAdmin(@PathVariable String username, @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateUserByAdmin(username, request));
    }



    @Operation(summary = "Delete own account", description = "Allows a user to delete their account by providing a reason")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized")
    })
    @PatchMapping("/delete")
    @PreAuthorize("hasRole('USER')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GenericResponse<?>> deleteUserByAdmin(
            @PathVariable String username,
            @Valid @RequestBody DeleteUserRequest request) {
        userService.deleteUserByAdmin(username, request.getReason());
        return ResponseEntity.ok(new GenericResponse<>("User deleted successfully", null, "DELETED", new Metadata()));
    }
}
