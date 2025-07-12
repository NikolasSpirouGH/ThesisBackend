package com.cloud_ml_app_thesis.dto.request.category;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CategoryDeleteRequest {

    @NotNull(message = "Category ID must be provided.")
    @Positive(message = "Category ID must be a positive integer.")
    private Integer categoryId;

    private String reason; // Optional: user can explain why
}