package com.cloud_ml_app_thesis.dto.train;

public record CustomTrainMetadata (
    String datasetKey,
    String datasetBucket,
    Integer datasetConfigurationId,
    Integer algorithmId,
    String paramsJsonKey,
    String paramsJsonBucket
) {}
