package com.cloud_ml_app_thesis.config;

import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BucketResolver {

    @Value("${minio.bucket.datasets}")
    private String datasetBucket;

    @Value("${minio.bucket.models}")
    private String modelBucket;

    @Value("${minio.bucket.predictions}")
    private String predictionBucket;

    @Value("${minio.bucket.algorithms}")
    private String algorithmBucket;

    @Value("${minio.bucket.results}")
    private String predictionResultsBucket;

    @Value("${minio.bucket.metrics}")
    private String metricsBucket;

    @Value("${minio.bucket.parameters}")
    private String parametersBucket;

    public String resolve(BucketTypeEnum type) {
        return switch (type) {
            case TRAIN_DATASET -> datasetBucket;
            case PREDICT_DATASET -> predictionBucket;
            case MODEL -> modelBucket;
            case CUSTOM_ALGORITHM -> algorithmBucket;
            case PREDICTION_RESULTS -> predictionResultsBucket;
            case METRICS -> metricsBucket;
            case PARAMETERS -> parametersBucket;
        };
    }
}
