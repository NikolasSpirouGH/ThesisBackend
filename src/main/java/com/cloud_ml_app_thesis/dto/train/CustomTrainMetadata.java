package com.cloud_ml_app_thesis.dto.train;

public record CustomTrainMetadata (
    String datasetKey,
    String datasetBucket,
    Integer datasetConfigurationId,
    Integer algorithmId,
    String paramsJsonKey,
    String paramsJsonBucket,
    Integer trainingId  // Pre-created training ID (for retrain mode), null for fresh training
) {
    // Constructor for backwards compatibility (fresh training without pre-created Training)
    public CustomTrainMetadata(String datasetKey, String datasetBucket, Integer datasetConfigurationId,
                               Integer algorithmId, String paramsJsonKey, String paramsJsonBucket) {
        this(datasetKey, datasetBucket, datasetConfigurationId, algorithmId, paramsJsonKey, paramsJsonBucket, null);
    }
}
