package com.cloud_ml_app_thesis.dto.train;

public record RetrainTrainingDetailsDTO(
        Integer trainingId,
        Integer modelId,
        Integer algorithmId,
        Integer algorithmConfigurationId,
        Integer customAlgorithmConfigurationId,
        String algorithmName,
        String algorithmOptions,
        Integer datasetId,
        Integer datasetConfigurationId,
        String datasetName,
        String basicAttributesColumns,
        String targetColumn,
        String status
) {}
