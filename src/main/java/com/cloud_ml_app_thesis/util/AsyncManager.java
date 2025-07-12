package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.train.CustomTrainMetadata;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.service.CustomTrainingService;
import com.cloud_ml_app_thesis.service.CustomPredictionService;
import com.cloud_ml_app_thesis.service.MinioService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncManager {


    private final CustomTrainingService trainingService;
    private final CustomPredictionService customPredictionService;
    private final BucketResolver bucketResolver;
    private final MinioService minioService;

    @Async
    @Transactional
    public CompletableFuture<Void> trainAsync(String taskId, User user, CustomTrainMetadata metadata) {
        log.info("🔍 [ASYNC] Training started [taskId={}]", taskId);
        try {
            trainingService.train(taskId, user, metadata);
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            CompletableFuture.failedFuture(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async
    @Transactional
    public CompletableFuture<String> predict(String taskId, Integer modelId, String datasetKey, User user) {
        log.info("🔍 [ASYNC] Prediction started [taskId={}]", taskId);
        try {
            customPredictionService.executeCustom(taskId, modelId, datasetKey, user);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
           CompletableFuture.failedFuture(e);
           return CompletableFuture.failedFuture(e);
        }
    }
}

