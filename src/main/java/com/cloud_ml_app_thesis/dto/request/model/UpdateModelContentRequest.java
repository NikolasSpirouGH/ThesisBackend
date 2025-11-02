package com.cloud_ml_app_thesis.dto.request.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateModelContentRequest {
    @NotNull(message = "Training ID is required")
    private Integer newTrainingId;
}
