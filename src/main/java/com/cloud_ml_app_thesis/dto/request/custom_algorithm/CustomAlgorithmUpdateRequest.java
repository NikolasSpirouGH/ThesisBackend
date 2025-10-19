package com.cloud_ml_app_thesis.dto.request.custom_algorithm;

import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomAlgorithmUpdateRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 50, message = "Version must not exceed 50 characters")
    private String version;

    private AlgorithmAccessibiltyEnum accessibility;

    private List<String> keywords;
}
