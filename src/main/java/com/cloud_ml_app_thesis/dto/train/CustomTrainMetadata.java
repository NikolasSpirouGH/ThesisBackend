package com.cloud_ml_app_thesis.dto.train;

import com.cloud_ml_app_thesis.entity.CustomAlgorithm;

public record CustomTrainMetadata (
    String datasetKey,
    String datasetBucket,
    Integer datasetConfigurationId,
    Integer algorithmId,
    String paramsJsonKey,
    String paramsJsonBucket
) {}
