package com.cloud_ml_app_thesis.dto.response.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelExecutionDTO {
    private Integer id;
    private String modelName;
    private String modelType; // "WEKA" or "CUSTOM"
    private String algorithmName;
    private String datasetName;
    private ZonedDateTime executedAt;
    private String status; // "IN_PROGRESS", "FINISHED", "FAILED"
    private String predictionResult;
    private Integer modelId;
    private Integer datasetId;
    private boolean hasResultFile;
}