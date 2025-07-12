package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.user.*;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@Tag(name = "User Management", description = "Endpoints for managing users")

@RestController
@RequestMapping("/api/users")

public class UserController {

    @Autowired
    private UserService userService;

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

    @Operation(summary = "Change user password", description = "Allows a user to change their password by providing the old one and a matching new password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error or password mismatch"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PatchMapping("/change-password")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<GenericResponse<?>> changePassword(
            @AuthenticationPrincipal AccountDetails userDetails,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        return ResponseEntity.ok(userService.changePassword(userDetails.getUser(), request));
    }

    @Operation(summary = "Request password reset", description = "Allows a user to request a password reset by providing their email address.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset email sent successfully."),
            @ApiResponse(responseCode = "400", description = "Invalid email address provided.")
    })
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<GenericResponse<?>> forgotPassword(@Valid @RequestBody EmailRequest userEmailRequest) {
            userService.resetPasswordRequest(userEmailRequest.getEmail());
        return ResponseEntity.ok(new GenericResponse<>(null, null, "If the email exists, a password reset link has been sent.", new Metadata()));
    }

    @Operation(summary = "Reset Password", description = "Allows a user to reset their password using a valid token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset successfully."),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token.")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<GenericResponse<?>> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        Optional<User> user = userService.validatePasswordResetToken(resetPasswordRequest.getToken());
        if (user.isPresent()) {
            userService.resetPassword(user.get(), resetPasswordRequest.getPassword());
            return ResponseEntity.ok(new GenericResponse<>(null, null, "Password has been reset successfully.", new Metadata()));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new GenericResponse<>(null, "Bad Request", "Invalid or expired token.", new Metadata()));
        }
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
