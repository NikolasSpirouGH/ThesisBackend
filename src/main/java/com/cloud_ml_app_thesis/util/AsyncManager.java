package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.dto.train.CustomTrainMetadata;
import com.cloud_ml_app_thesis.dto.train.PredefinedTrainMetadata;
import com.cloud_ml_app_thesis.dto.train.WekaContainerTrainMetadata;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.service.CustomTrainingService;
import com.cloud_ml_app_thesis.service.CustomModelExecutionService;
import com.cloud_ml_app_thesis.service.ModelExecutionService;
import com.cloud_ml_app_thesis.service.TrainService;
import com.cloud_ml_app_thesis.service.WekaContainerTrainingService;
import com.cloud_ml_app_thesis.service.WekaContainerPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncManager {

    private final CustomTrainingService customTrainingService;
    private final CustomModelExecutionService customModelExecutionService;
    private final TrainService trainService;
    private final ModelExecutionService modelExecutionService;
    private final WekaContainerTrainingService wekaContainerTrainingService;
    private final WekaContainerPredictionService wekaContainerPredictionService;

    @Async
    public CompletableFuture<Void> customTrainAsync(String taskId, UUID userId, String username, CustomTrainMetadata metadata) {
        log.info("🔍 [ASYNC] Training started [taskId={}]", taskId);
        try {
            customTrainingService.trainCustom(taskId, userId, username, metadata);
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async
    public CompletableFuture<Void> trainAsync(String taskId, User user, PredefinedTrainMetadata metadata) {
        log.info("🔍 [ASYNC] Training started [taskId={}]", taskId);
        try {
            trainService.train(taskId, user, metadata);
            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async
    public CompletableFuture<String> predictCustom(String taskId, Integer modelId, String datasetKey, User user) {
        log.info("🔍 [ASYNC] Prediction started [taskId={}]", taskId);
        try {
            customModelExecutionService.executeCustom(taskId, modelId, datasetKey, user);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
           return CompletableFuture.failedFuture(e);
        }
    }

    @Async
    public CompletableFuture<String> predictPredefined(String taskId, Integer modelId, String datasetKey, User user) {
        log.info("🔍 [ASYNC] Prediction started [taskId={}]", taskId);
        try {
            modelExecutionService.executePredefined(taskId, modelId, datasetKey, user);

            return CompletableFuture.completedFuture(null);

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Run Weka (predefined) algorithm training as a containerized Kubernetes job.
     */
    @Async
    public CompletableFuture<Void> trainWekaContainerAsync(String taskId, UUID userId, String username, WekaContainerTrainMetadata metadata) {
        log.info("🔍 [ASYNC] Weka container training started [taskId={}]", taskId);
        try {
            wekaContainerTrainingService.trainWeka(taskId, userId, username, metadata);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Run Weka (predefined) algorithm prediction as a containerized Kubernetes job.
     */
    @Async
    public CompletableFuture<String> predictWekaContainer(String taskId, Integer modelId, String datasetKey, User user) {
        log.info("🔍 [ASYNC] Weka container prediction started [taskId={}]", taskId);
        try {
            wekaContainerPredictionService.executeWeka(taskId, modelId, datasetKey, user);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
