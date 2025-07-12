package com.cloud_ml_app_thesis.dto.request.algorithm;

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
@Getter
@Setter
public class AlgorithmUpdateRequest {

    @Positive
    private Integer newId;

    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    @Size(max = 5000, message = "Options cannot exceed 5000 characters")
    private String options;

    @Size(max = 5000, message = "Options description cannot exceed 5000 characters")
    private String optionsDescription;

    @Size(max = 5000, message = "Default options cannot exceed 5000 characters")
    private String defaultOptions;

    @Size(max = 100, message = "Default options cannot exceed 100 characters")
    private String className;

}