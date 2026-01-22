package com.cloud_ml_app_thesis.dto.train;

public record RetrainModelOptionDTO(
        Integer modelId,
        String modelName,
        Integer trainingId,
        String algorithmName,
        String datasetName,
        String status,
        String trainingType  // "PREDEFINED" (Weka) or "CUSTOM"
) {}
