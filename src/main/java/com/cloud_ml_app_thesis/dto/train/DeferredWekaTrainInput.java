package com.cloud_ml_app_thesis.dto.train;

import java.nio.file.Path;

/**
 * Deferred input for Weka (predefined algorithm) training.
 * Mirrors TrainingStartRequest fields but with Path instead of MultipartFile.
 */
public record DeferredWekaTrainInput(
    Path datasetTempFile,              // null if using existing dataset
    String datasetOriginalFilename,
    String datasetContentType,
    long datasetFileSize,
    String algorithmId,
    String algorithmConfigurationId,
    String datasetId,
    String datasetConfigurationId,
    String basicCharacteristicsColumns,
    String targetClassColumn,
    String options,
    String trainingId,                 // retrain
    String modelId                     // retrain
) {}
