package com.cloud_ml_app_thesis.dto.train;

/**
 * Metadata for containerized Weka training.
 * Similar to CustomTrainMetadata but for predefined Weka algorithms.
 *
 * Unlike PredefinedTrainMetadata which carries loaded Instances in memory,
 * this DTO carries file references so the container can download and load the data.
 */
public record WekaContainerTrainMetadata(
        Integer trainingId,
        Integer datasetConfigurationId,
        Integer algorithmConfigurationId,
        String datasetBucket,
        String datasetKey,
        String targetColumn,
        String basicAttributesColumns
) {}
