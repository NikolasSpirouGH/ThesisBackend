package com.cloud_ml_app_thesis.dto.train;

public record RetrainTrainingOptionDTO(
        Integer trainingId,
        String algorithmName,
        String datasetName,
        String status,
        Integer modelId
) {}
