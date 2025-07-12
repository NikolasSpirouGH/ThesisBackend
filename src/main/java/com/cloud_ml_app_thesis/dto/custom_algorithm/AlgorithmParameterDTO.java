package com.cloud_ml_app_thesis.dto.custom_algorithm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlgorithmParameterDTO {

    @NotBlank
    private String name;

    @NotBlank
    private String type; // e.g., "int", "float", "string"

    private String value;

    @Size(max = 100)
    private String description;

    private String range; // e.g. "1-100"
}
