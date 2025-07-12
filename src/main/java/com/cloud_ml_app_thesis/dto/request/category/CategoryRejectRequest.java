package com.cloud_ml_app_thesis.dto.request.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryRejectRequest {
    @NotBlank
    @Size(max = 5000, message = "Rejection reason cannot exceed 5000 characters")
    private String rejectionReason;
}
