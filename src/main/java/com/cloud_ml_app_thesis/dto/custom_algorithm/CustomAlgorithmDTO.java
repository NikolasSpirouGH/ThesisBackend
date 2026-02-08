package com.cloud_ml_app_thesis.dto.custom_algorithm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomAlgorithmDTO {
    private Integer id;
    private String name;
    private String description;
    private String version; // From active image
    private String accessibility;
    private String ownerUsername;
    @JsonProperty("isOwner")
    private boolean isOwner;
    private List<String> keywords;
    private String createdAt;
    private String executionMode;
}