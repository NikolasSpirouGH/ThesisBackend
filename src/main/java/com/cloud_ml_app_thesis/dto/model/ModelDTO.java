package com.cloud_ml_app_thesis.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDTO {
    private Integer id;
    private String name;
    private String description;
    private String dataDescription;
    private Integer trainingId;
    private String algorithmName;
    private String datasetName;
    private String modelType;
    private String algorithmType;
    private String status;
    private String accessibility;
    private String categoryName;
    private Integer categoryId;
    private Set<String> keywords;
    private ZonedDateTime finishedAt;
    private ZonedDateTime finalizationDate;
    private boolean finalized;
    private String ownerUsername;
}
