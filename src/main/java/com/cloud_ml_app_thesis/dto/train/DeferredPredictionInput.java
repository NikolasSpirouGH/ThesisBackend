package com.cloud_ml_app_thesis.dto.train;

import java.nio.file.Path;

/**
 * Deferred input for prediction.
 * Contains temp file path instead of MultipartFile to enable async processing.
 */
public record DeferredPredictionInput(
    Path predictionTempFile,
    String originalFilename,
    String contentType,
    long fileSize,
    Integer modelId,
    Integer existingDatasetId       // nullable - if set, use this instead of temp file
) {}
