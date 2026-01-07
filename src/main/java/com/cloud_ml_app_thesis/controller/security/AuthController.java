package com.cloud_ml_app_thesis.controller.security;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.user.EmailRequest;
import com.cloud_ml_app_thesis.dto.request.user.LoginRequest;
import com.cloud_ml_app_thesis.dto.request.user.PasswordChangeRequest;
import com.cloud_ml_app_thesis.dto.request.user.ResetPasswordRequest;
import com.cloud_ml_app_thesis.dto.request.user.UserRegisterRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.service.security.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@Tag(name = "Authentication Management", description = "Endpoints for managing authentication")

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {


    private final AuthService authService;

    @Operation(summary = "Register a new user", description = "Allows anyone to register providing the required fields")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    public ResponseEntity<GenericResponse<?>> registerUser(@Valid @RequestBody UserRegisterRequest registerRequest) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(registerRequest));
    }


    @Operation(summary = "Login a user", description = "Authenticates a user and returns a JWT token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<GenericResponse<?>> login(@Valid @RequestBody LoginRequest request) {
        GenericResponse<?> response = authService.login(request);

        if (response != null && "INVALID_CREDENTIALS".equals(response.getErrorCode())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout", description = "Revokes the user's token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logout successful"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        authService.logout(token);
        return ResponseEntity.ok(new GenericResponse<>("Successfully logged out", null, "LOGOUT", new Metadata()));
    }

    @Operation(summary = "Change user password", description = "Allows a user to change their password by providing the old one and a matching new password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error or password mismatch"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PatchMapping("/change-password")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
    public ResponseEntity<GenericResponse<?>> changePassword(
            @AuthenticationPrincipal AccountDetails userDetails,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        return ResponseEntity.ok(authService.changePassword(userDetails.getUser(), request));
    }

    @Operation(summary = "Request password reset", description = "Allows a user to request a password reset by providing their email address.")
    @PostMapping("/forgot-password")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset email sent successfully."),
            @ApiResponse(responseCode = "400", description = "Invalid email address provided.")
    })
    @PreAuthorize("permitAll()")
    public ResponseEntity<GenericResponse<?>> forgotPassword(@Valid @RequestBody EmailRequest userEmailRequest) {
            authService.resetPasswordRequest(userEmailRequest.getEmail());
        return ResponseEntity.ok(new GenericResponse<>(null, null, "If the email exists, a password reset link has been sent.", new Metadata()));
    }

    @Operation(summary = "Reset Password", description = "Allows a user to reset their password using a valid token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset successfully."),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token.")
    })
    @PostMapping("/reset-password")
    @PreAuthorize("permitAll()")
    public ResponseEntity<GenericResponse<?>> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        Optional<User> user = authService.validatePasswordResetToken(resetPasswordRequest.getToken());
        if (user.isPresent()) {
            authService.resetPassword(user.get(), resetPasswordRequest.getNewPassword());
            return ResponseEntity.ok(new GenericResponse<>(null, null, "Password has been reset successfully.", new Metadata()));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new GenericResponse<>(null, "Bad Request", "Invalid or expired token.", new Metadata()));
        }
    }


}
