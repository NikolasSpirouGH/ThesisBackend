package com.cloud_ml_app_thesis.dto.request.algorithm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;


@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class AlgorithmCreateRequest {

    @Size(max = 255, message = "Name must be at most 255 characters")
    @NotBlank(message = "Algorithm name is required")
    private String name;

    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    @NotBlank(message = "Algorithm description is required")
    private String description;

    @Size(max = 5000, message = "Options cannot exceed 5000 characters")
    @NotBlank(message = "Algorithm options is required")
    private String options;

    @Size(max = 5000, message = "Options description cannot exceed 5000 characters")
    @NotBlank(message = "Algorithm optionsDescription is required")
    private String optionsDescription;

    @Size(max = 5000, message = "Default options cannot exceed 5000 characters")
    @NotBlank(message = "Algorithm defaultOptions is required")
    private String defaultOptions;

    @Size(max = 100, message = "Default options cannot exceed 100 characters")
    @NotBlank(message = "Algorithm className is required")
    private String className;

}
