package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.entity.CustomAlgorithm;
import com.cloud_ml_app_thesis.entity.CustomAlgorithmImage;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.model.ModelExecution;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.ModelExecutionStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.model.ModelExecutionRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.ModelExecutionStatusRepository;
import com.cloud_ml_app_thesis.strategy.CustomExecutionStrategy;
import com.cloud_ml_app_thesis.strategy.ExecutionStrategyResolver;
import com.cloud_ml_app_thesis.util.ContainerRunner;
import com.cloud_ml_app_thesis.util.FileUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.cloud_ml_app_thesis.exception.UserInitiatedStopException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomModelExecutionService {

    private final TaskStatusRepository taskStatusRepository;
    private final ModelRepository modelRepository;
    private final BucketResolver bucketResolver;
    private final MinioService minioService;
    private final ModelExecutionStatusRepository modelExecutionStatusRepository;
    private final ModelExecutionRepository modelExecutionRepository;
    private final ModelService modelService;
    private final ContainerRunner containerRunner;
    private final ExecutionStrategyResolver executionStrategyResolver;
    private final TaskStatusService taskStatusService;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executeCustom(String taskId, Integer modelId, String datasetKey, User user) {
        boolean complete = false;
        ModelExecution execution = null;
        Path dataDir = null;
        Path outputDir = null;
        String uploadedPredKey = null;
        String predBucket = null;
        log.info("🎯 [SYNC] Starting prediction [taskId={}] for modelId={} by user={}", taskId, modelId, user.getUsername());

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Missing task record"));

        task.setStatus(TaskStatusEnum.RUNNING);
        taskStatusRepository.save(task);
        entityManager.detach(task);

        try {
            // Check for stop request before starting
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop for task " + taskId);
            }

            // 1. Ανάκτηση μοντέλου με eager loading για όλες τις σχέσεις
            Model model = modelRepository.findByIdWithTrainingDetails(modelId)
                    .orElseThrow(() -> new EntityNotFoundException("Model with ID " + modelId + " not found"));

            if (model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE)
                    && !user.getUsername().equals(model.getTraining().getUser().getUsername())) {
                throw new AuthorizationDeniedException("You are not allowed to run this model");
            }

            if (!model.getModelType().getName().equals(ModelTypeEnum.CUSTOM)) {
                taskStatusService.taskFailed(taskId, "This model is not Docker Based");
                throw new IllegalArgumentException("This model is not Docker-based");
            }

            if (model.getFinalizationDate() == null) {
                throw new IllegalArgumentException("You cannot predict with this model. You have to finalize it first");
            }

            String inputBucket = bucketResolver.resolve(BucketTypeEnum.PREDICT_DATASET);
            String modelBucket = bucketResolver.resolve(BucketTypeEnum.MODEL);

            // 2. Δημιουργία shared paths
            //Path basePath = Paths.get(System.getenv("SHARED_VOLUME")).toAbsolutePath();
            Path basePath = FileUtil.getSharedPathRoot();
            dataDir = Files.createTempDirectory(basePath, "predict-ds-");
            outputDir = Files.createTempDirectory(basePath, "predict-out-");
            setDirectoryPermissions(dataDir);
            setDirectoryPermissions(outputDir);

            Files.createDirectories(dataDir);
            Files.createDirectories(outputDir);

            // 3. Download dataset to temp file
            Path datasetPath = minioService.downloadObjectToTempFile(inputBucket, datasetKey);
            Path testDataPath = dataDir.resolve("test_data.csv");
            Files.copy(datasetPath, testDataPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Prediction dataset copied to {}", testDataPath);

            // 4. Download model and artifacts from MinIO
            String modelKey = minioService.extractMinioKey(model.getModelUrl());
            Path modelPath = minioService.downloadObjectToTempFile(modelBucket, modelKey);
            log.info("Trained model downloaded from {}/{}", modelBucket, modelKey);

            // Download optional JSON artifacts
            Path labelMappingPath = null;
            if (model.getLabelMappingUrl() != null && !model.getLabelMappingUrl().isBlank()) {
                try {
                    String labelMappingKey = minioService.extractMinioKey(model.getLabelMappingUrl());
                    labelMappingPath = minioService.downloadObjectToTempFile(modelBucket, labelMappingKey);
                    log.info("Label mapping downloaded from {}/{}", modelBucket, labelMappingKey);
                } catch (Exception e) {
                    log.warn("Could not download label_mapping.json: {}", e.getMessage());
                }
            }

            Path featureColumnsPath = null;
            if (model.getFeatureColumnsUrl() != null && !model.getFeatureColumnsUrl().isBlank()) {
                try {
                    String featureColumnsKey = minioService.extractMinioKey(model.getFeatureColumnsUrl());
                    featureColumnsPath = minioService.downloadObjectToTempFile(modelBucket, featureColumnsKey);
                    log.info("Feature columns downloaded from {}/{}", modelBucket, featureColumnsKey);
                } catch (Exception e) {
                    log.warn("Could not download feature_columns.json: {}", e.getMessage());
                }
            }

            // 5. Resolve execution strategy and prepare prediction data
            CustomAlgorithm algorithm = model.getTraining().getCustomAlgorithmConfiguration().getAlgorithm();
            CustomAlgorithmImage activeImage = algorithm.getImages().stream()
                    .filter(CustomAlgorithmImage::isActive)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No active image found"));

            CustomExecutionStrategy executionStrategy = executionStrategyResolver.resolve(algorithm.getExecutionMode());
            log.info("Using execution strategy: {} for prediction", algorithm.getExecutionMode());

            // Delegate strategy-specific preparation (Python template injects predict.py + algorithm.py; BYOC copies model as-is)
            executionStrategy.preparePredictionData(activeImage.getName(), dataDir, outputDir,
                    modelPath, labelMappingPath, featureColumnsPath, testDataPath);

            // Run prediction container via strategy
            executionStrategy.executePrediction(activeImage.getName(), dataDir, outputDir,
                    jobName -> taskStatusService.updateJobName(taskId, jobName));

            // 6. Collect prediction results via strategy
            File predictedFile = executionStrategy.collectPredictionResults(outputDir);

            // Check for stop request after Docker preparation
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop after Docker setup for task " + taskId);
            }

            // 7. Save ModelExecution
            execution = new ModelExecution();
            execution.setModel(model);
            execution.setExecutedByUser(user);
            execution.setDataset(model.getTraining().getDatasetConfiguration().getDataset());
            execution.setExecutedAt(ZonedDateTime.now());
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.RUNNING)
                    .orElseThrow(() -> new EntityNotFoundException("Execution status not found")));
            modelExecutionRepository.save(execution);
            entityManager.detach(execution);

            // 8. Upload σε MinIO
            String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
            String predKey = user.getUsername() + "_" + timestamp + "_" + predictedFile.getName();
            predBucket = bucketResolver.resolve(BucketTypeEnum.PREDICTION_RESULTS);

            try (InputStream in = new FileInputStream(predictedFile)) {
                minioService.uploadToMinio(in, predBucket, predKey, predictedFile.length(), "text/csv");
                uploadedPredKey = predKey;  // Track for cleanup on stop
            }

            String resultUrl = modelService.generateMinioUrl(predBucket, predKey);
            complete = true;

            // Check for stop request after upload
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop after result upload for task " + taskId);
            }

            // ⬇️ Update ModelExecution in separate transaction
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            final Integer executionId = execution.getId();
            final String finalResultUrl = resultUrl;
            transactionTemplate.executeWithoutResult(status -> {
                ModelExecution exec = modelExecutionRepository.findById(executionId)
                        .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                exec.setPredictionResult(finalResultUrl);
                exec.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.COMPLETED).orElseThrow());
                modelExecutionRepository.saveAndFlush(exec);
            });

            // ⬇️ Complete async task in separate transaction
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            final Integer finalExecutionId = execution.getId();
            transactionTemplate.executeWithoutResult(status -> {
                if (taskStatusService.stopRequested(taskId)) {
                    return; // Don't complete if stopped
                }
                AsyncTaskStatus freshTask = taskStatusRepository.findById(taskId).orElseThrow();
                freshTask.setStatus(TaskStatusEnum.COMPLETED);
                freshTask.setFinishedAt(ZonedDateTime.now());
                freshTask.setExecutionId(finalExecutionId);
                taskStatusRepository.saveAndFlush(freshTask);
            });

            log.info("✅ Prediction complete [taskId={}]. Output at: {}", taskId, resultUrl);

        } catch (UserInitiatedStopException e) {
            log.warn("🛑 Custom prediction manually stopped by user [taskId={}]: {}", taskId, e.getMessage());

            // Cleanup MinIO files if they were uploaded
            if (uploadedPredKey != null && predBucket != null) {
                try {
                    minioService.deleteObject(predBucket, uploadedPredKey);
                    log.info("🧹 Cleaned up prediction result from MinIO: {}/{}", predBucket, uploadedPredKey);
                } catch (Exception cleanupEx) {
                    log.warn("⚠️ Failed to clean up prediction result from MinIO: {}", cleanupEx.getMessage());
                }
            }

            taskStatusService.taskStoppedExecution(taskId, execution != null ? execution.getId() : null);

            ModelExecutionStatusEnum finalStatus = complete
                    ? ModelExecutionStatusEnum.COMPLETED
                    : ModelExecutionStatusEnum.FAILED;

            // Update execution status in separate transaction
            if (execution != null) {
                final Integer executionId = execution.getId();
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.executeWithoutResult(status -> {
                    ModelExecution exec = modelExecutionRepository.findById(executionId)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                    exec.setStatus(modelExecutionStatusRepository.findByName(finalStatus)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecutionStatus not found")));
                    modelExecutionRepository.saveAndFlush(exec);
                });
            }
        } catch (Exception e) {
            // Check if this was actually a user-initiated stop (job cancelled)
            if (taskStatusService.stopRequested(taskId)) {
                log.warn("🛑 Prediction stopped due to job cancellation [taskId={}]: {}", taskId, e.getMessage());

                // Cleanup MinIO files if they were uploaded
                if (uploadedPredKey != null && predBucket != null) {
                    try {
                        minioService.deleteObject(predBucket, uploadedPredKey);
                        log.info("🧹 Cleaned up prediction result from MinIO: {}/{}", predBucket, uploadedPredKey);
                    } catch (Exception cleanupEx) {
                        log.warn("⚠️ Failed to clean up prediction result from MinIO: {}", cleanupEx.getMessage());
                    }
                }

                taskStatusService.taskStoppedExecution(taskId, execution != null ? execution.getId() : null);

                if (execution != null) {
                    final Integer executionId = execution.getId();
                    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    transactionTemplate.executeWithoutResult(status -> {
                        ModelExecution exec = modelExecutionRepository.findById(executionId)
                                .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                        exec.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FAILED)
                                .orElseThrow(() -> new EntityNotFoundException("ModelExecutionStatus not found")));
                        modelExecutionRepository.saveAndFlush(exec);
                    });
                }
                return; // Exit gracefully for user-initiated stop
            }

            log.error("❌ Custom prediction failed [taskId={}]: {}", taskId, e.getMessage(), e);

            // Task -> FAILED
            taskStatusService.taskFailed(taskId, e.getMessage());

            // ModelExecution -> FAILED in separate transaction
            if (execution != null) {
                final Integer executionId = execution.getId();
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.executeWithoutResult(status -> {
                    ModelExecution exec = modelExecutionRepository.findById(executionId)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                    exec.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FAILED)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecutionStatus FAILED not found")));
                    modelExecutionRepository.saveAndFlush(exec);
                });
            }

            throw new RuntimeException("Custom prediction failed: " + e.getMessage(), e);
        } finally {
            try {
                if (dataDir != null) FileUtils.deleteDirectory(dataDir.toFile());
                if (outputDir != null) FileUtils.deleteDirectory(outputDir.toFile());
            } catch (IOException cleanupEx) {
                log.warn("⚠️ Failed to clean up temp directories: {}", cleanupEx.getMessage());
            }
        }
    }

    private void setDirectoryPermissions(Path dir) {
        try {
            java.nio.file.attribute.PosixFileAttributeView view = java.nio.file.Files.getFileAttributeView(dir, java.nio.file.attribute.PosixFileAttributeView.class);
            if (view != null) {
                view.setPermissions(java.util.EnumSet.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                        java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                        java.nio.file.attribute.PosixFilePermission.GROUP_WRITE,
                        java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                        java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
                        java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE,
                        java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
                ));
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to adjust permissions for {}: {}", dir, e.getMessage());
        }
    }



}


