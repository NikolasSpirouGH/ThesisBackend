package com.cloud_ml_app_thesis.dto.request.algorithm_configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class AlgorithmConfigurationUpdateRequest {
    @Positive
    private Integer newId;
    //The id will be a PathVariable
    @Size(max = 250, message = "Default options cannot exceed 250 characters")
    private String options;
}
