package com.cloud_ml_app_thesis.dto.train;

import java.nio.file.Path;

/**
 * Deferred input for custom training.
 * Contains temp file paths instead of MultipartFile to enable async processing.
 */
public record DeferredCustomTrainInput(
    Path datasetTempFile,           // null if retraining from existing dataset
    String datasetOriginalFilename,
    String datasetContentType,
    long datasetFileSize,
    Path paramsTempFile,            // null if no params file
    String paramsOriginalFilename,
    Integer algorithmId,
    Integer retrainFromTrainingId,  // nullable
    Integer retrainFromModelId,     // nullable
    String basicAttributesColumns,  // nullable
    String targetColumn             // nullable
) {}
