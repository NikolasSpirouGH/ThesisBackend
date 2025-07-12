package com.cloud_ml_app_thesis.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteUserRequest {

    @NotBlank(message = "Reason for deletion is required")
    private String reason;
}