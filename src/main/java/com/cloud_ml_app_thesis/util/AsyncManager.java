package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.dto.train.CustomTrainMetadata;
import com.cloud_ml_app_thesis.dto.train.DeferredCustomTrainInput;
import com.cloud_ml_app_thesis.dto.train.DeferredPredictionInput;
import com.cloud_ml_app_thesis.dto.train.DeferredWekaTrainInput;
import com.cloud_ml_app_thesis.dto.train.PredefinedTrainMetadata;
import com.cloud_ml_app_thesis.dto.train.WekaContainerTrainMetadata;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.service.AsyncTrainingSetupService;
import com.cloud_ml_app_thesis.service.CustomTrainingService;
import com.cloud_ml_app_thesis.service.CustomModelExecutionService;
import com.cloud_ml_app_thesis.service.ModelExecutionService;
import com.cloud_ml_app_thesis.service.TaskStatusService;
import com.cloud_ml_app_thesis.service.TrainService;
import com.cloud_ml_app_thesis.service.WekaContainerTrainingService;
import com.cloud_ml_app_thesis.service.WekaContainerPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final AsyncTrainingSetupService setupService;
    private final TaskStatusService taskStatusService;
    private final ModelRepository modelRepository;

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

    // =========================================================================
    // NEW: Deferred setup + training/prediction methods for non-blocking taskId return
    // =========================================================================

    /**
     * Setup custom training (MinIO upload + entity creation) then run training.
     * Called after taskId is returned to client.
     */
    @Async
    public CompletableFuture<Void> setupAndTrainCustom(String taskId, UUID userId, String username, User user, DeferredCustomTrainInput input) {
        log.info("🔍 [ASYNC] Setup + custom training started [taskId={}]", taskId);
        try {
            CustomTrainMetadata metadata = setupService.prepareCustomTraining(user, input);
            customTrainingService.trainCustom(taskId, userId, username, metadata);
        } catch (Exception e) {
            log.error("Custom training failed [taskId={}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, e.getMessage());
        } finally {
            cleanupTempFiles(input.datasetTempFile(), input.paramsTempFile());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Setup Weka training (MinIO upload + entity creation) then run training.
     * Called after taskId is returned to client.
     */
    @Async
    public CompletableFuture<Void> setupAndTrainWeka(String taskId, UUID userId, String username, User user, DeferredWekaTrainInput input) {
        log.info("🔍 [ASYNC] Setup + Weka training started [taskId={}]", taskId);
        try {
            WekaContainerTrainMetadata metadata = setupService.prepareWekaTraining(user, input);
            wekaContainerTrainingService.trainWeka(taskId, userId, username, metadata);
        } catch (Exception e) {
            log.error("Weka training failed [taskId={}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, e.getMessage());
        } finally {
            cleanupTempFiles(input.datasetTempFile());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Setup prediction (MinIO upload) then run prediction.
     * Called after taskId is returned to client.
     */
    @Async
    public CompletableFuture<Void> setupAndPredict(String taskId, User user, DeferredPredictionInput input) {
        log.info("🔍 [ASYNC] Setup + prediction started [taskId={}]", taskId);
        try {
            String datasetKey = setupService.preparePrediction(user, input);
            Model model = modelRepository.findByIdWithTrainingDetails(input.modelId())
                    .orElseThrow(() -> new IllegalArgumentException("Model not found: " + input.modelId()));
            switch (model.getModelType().getName()) {
                case CUSTOM -> customModelExecutionService.executeCustom(taskId, input.modelId(), datasetKey, user);
                case PREDEFINED -> wekaContainerPredictionService.executeWeka(taskId, input.modelId(), datasetKey, user);
            }
        } catch (Exception e) {
            log.error("Prediction failed [taskId={}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, e.getMessage());
        } finally {
            cleanupTempFiles(input.predictionTempFile());
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Cleanup temp files after async processing.
     */
    private void cleanupTempFiles(Path... paths) {
        for (Path p : paths) {
            if (p != null) {
                try {
                    Files.deleteIfExists(p);
                    log.debug("🗑️ Deleted temp file: {}", p);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", p, e);
                }
            }
        }
    }
}
