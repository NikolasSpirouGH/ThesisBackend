package com.cloud_ml_app_thesis.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class MinioConfig {

    @Value("${minio.url}")
    private String url;

    @Value("${minio.access.name}")
    private String accessKey;

    @Value("${minio.access.secret}")
    private String secretKey;

    @Value("${minio.bucket.datasets}")
    private String datasetsBucketName;

    @Value("${minio.bucket.models}")
    private String modelsBucketName;

    @Value("${minio.bucket.algorithms}")
    private String algorithmsBucketName;

    @Value("${minio.bucket.results}")
    private String predictionResultsBucketName;

    @Value("${minio.bucket.predictions}")
    private String predictionsBucketName;

    @Value("${minio.bucket.metrics}")
    private String metricsBucketName;

    @Value("${minio.bucket.parameters}")
    private String parametersBucketName;

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(url)
                    .credentials(accessKey, secretKey)
                    .build();

            ensureBucketExists(minioClient, datasetsBucketName);

            ensureBucketExists(minioClient, modelsBucketName);

            ensureBucketExists(minioClient, predictionsBucketName);

            ensureBucketExists(minioClient, algorithmsBucketName);

            ensureBucketExists(minioClient, predictionResultsBucketName);

            ensureBucketExists(minioClient, metricsBucketName);

            ensureBucketExists(minioClient, parametersBucketName);

            return minioClient;
        } catch (MinioException e) {
            throw new RuntimeException("Error initializing Minio: " + e.getMessage());
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    private void ensureBucketExists(MinioClient minioClient, String bucketName) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Bucket '{}' created successfully.", bucketName);
        } else {
            log.info("Bucket '{}' already exists.", bucketName);
        }
    }
}