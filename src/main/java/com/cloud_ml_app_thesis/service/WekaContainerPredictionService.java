package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.config.PathResolver;
import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.model.ModelExecution;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.ModelExecutionStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.exception.UserInitiatedStopException;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.model.ModelExecutionRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.ModelExecutionStatusRepository;
import com.cloud_ml_app_thesis.util.ContainerRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Service for running Weka (predefined) algorithm predictions as containerized Kubernetes jobs.
 * Similar to CustomModelExecutionService but for Weka algorithms.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WekaContainerPredictionService {

    private final TaskStatusRepository taskStatusRepository;
    private final ModelRepository modelRepository;
    private final BucketResolver bucketResolver;
    private final MinioService minioService;
    private final ModelExecutionStatusRepository modelExecutionStatusRepository;
    private final ModelExecutionRepository modelExecutionRepository;
    private final ModelService modelService;
    private final ContainerRunner containerRunner;
    private final TaskStatusService taskStatusService;
    private final PathResolver pathResolver;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    private static final String WEKA_RUNNER_IMAGE = "thesisapp/weka-runner:latest";

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executeWeka(String taskId, Integer modelId, String datasetKey, User user) {
        boolean complete = false;
        ModelExecution execution = null;
        Path dataDir = null;
        Path outputDir = null;

        log.info("🎯 Starting Weka container prediction [taskId={}] for modelId={} by user={}",
                taskId, modelId, user.getUsername());

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

            // 1. Load model with all training details
            Model model = modelRepository.findByIdWithTrainingDetails(modelId)
                    .orElseThrow(() -> new EntityNotFoundException("Model with ID " + modelId + " not found"));

            // Authorization check
            if (model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE)
                    && !user.getUsername().equals(model.getTraining().getUser().getUsername())) {
                throw new AuthorizationDeniedException("You are not allowed to run this model");
            }

            // Type check
            if (!model.getModelType().getName().equals(ModelTypeEnum.PREDEFINED)) {
                taskStatusService.taskFailed(taskId, "This model is not Weka-based");
                throw new IllegalArgumentException("This model is not Weka-based");
            }

            // Get algorithm configuration
            AlgorithmConfiguration algorithmConfig = model.getTraining().getAlgorithmConfiguration();
            if (algorithmConfig == null) {
                throw new IllegalStateException("Model does not have an associated algorithm configuration");
            }

            DatasetConfiguration datasetConfig = model.getTraining().getDatasetConfiguration();
            if (datasetConfig == null) {
                throw new IllegalStateException("Model does not have an associated dataset configuration");
            }

            // 2. Create shared paths
            Path basePath = pathResolver.getSharedPathRoot();
            dataDir = Files.createTempDirectory(basePath, "weka-predict-ds-");
            outputDir = Files.createTempDirectory(basePath, "weka-predict-out-");
            setDirectoryPermissions(dataDir);
            setDirectoryPermissions(outputDir);

            log.info("📁 Prediction paths: dataDir={}, outputDir={}", dataDir, outputDir);

            // 3. Download prediction dataset to data directory
            String inputBucket = bucketResolver.resolve(BucketTypeEnum.PREDICT_DATASET);
            Path datasetPath = minioService.downloadObjectToTempFile(inputBucket, datasetKey);

            // Convert Excel to CSV if needed
            Path testDataFile = dataDir.resolve("test_data.csv");
            String datasetFileName = datasetPath.getFileName().toString().toLowerCase();
            if (datasetFileName.endsWith(".xls") || datasetFileName.endsWith(".xlsx")) {
                log.info("📊 Detected Excel file, converting to CSV...");
                String csvPath = com.cloud_ml_app_thesis.util.XlsToCsv.convertExcelToCsv(
                        Files.newInputStream(datasetPath), datasetFileName);
                Files.copy(Path.of(csvPath), testDataFile, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(Path.of(csvPath));
            } else {
                Files.copy(datasetPath, testDataFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("📥 Prediction dataset copied to: {}", testDataFile);

            // 4. Download trained model to output directory
            String modelBucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
            String modelKey = minioService.extractMinioKey(model.getModelUrl());
            Path modelPath = minioService.downloadObjectToTempFile(modelBucket, modelKey);
            Path modelInside = outputDir.resolve("model.ser");
            Files.copy(modelPath, modelInside, StandardCopyOption.REPLACE_EXISTING);
            log.info("📥 Trained model copied to: {}", modelInside);

            // 5. Create params.json with algorithm info
            Map<String, Object> params = new HashMap<>();
            params.put("algorithmType", algorithmConfig.getAlgorithmType().getName().name());
            params.put("targetColumn", datasetConfig.getTargetColumn());
            params.put("basicAttributesColumns", datasetConfig.getBasicAttributesColumns());

            Path paramsFile = dataDir.resolve("params.json");
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(paramsFile.toFile(), params);
            log.info("✅ Created params.json for prediction: {}", params);

            // 6. Check for stop request before running container
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop before Weka prediction for task " + taskId);
            }

            // 7. Run Weka prediction container
            log.info("🚀 Running Weka prediction container...");
            containerRunner.runWekaPredictionContainer(WEKA_RUNNER_IMAGE, dataDir, outputDir);

            // 8. Find output file (predictions.csv)
            File predictedFile = Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("predictions.csv"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No predictions.csv output found", null));

            log.info("✅ Found prediction output: {}", predictedFile.getName());

            // 9. Create ModelExecution record
            execution = new ModelExecution();
            execution.setModel(model);
            execution.setExecutedByUser(user);
            execution.setDataset(datasetConfig.getDataset());
            execution.setExecutedAt(ZonedDateTime.now());
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.RUNNING)
                    .orElseThrow(() -> new EntityNotFoundException("Execution status not found")));
            modelExecutionRepository.save(execution);
            entityManager.detach(execution);

            // Check for stop request
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop after prediction for task " + taskId);
            }

            // 10. Upload results to MinIO
            String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
            String predKey = user.getUsername() + "_" + timestamp + "_prediction.csv";
            String predBucket = bucketResolver.resolve(BucketTypeEnum.PREDICTION_RESULTS);

            try (InputStream in = new FileInputStream(predictedFile)) {
                minioService.uploadToMinio(in, predBucket, predKey, predictedFile.length(), "text/csv");
            }

            String resultUrl = modelService.generateMinioUrl(predBucket, predKey);
            complete = true;

            // Check for stop request after upload
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop after result upload for task " + taskId);
            }

            // 11. Update ModelExecution
            final Integer executionId = execution.getId();
            final String finalResultUrl = resultUrl;
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.executeWithoutResult(status -> {
                ModelExecution exec = modelExecutionRepository.findById(executionId)
                        .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                exec.setPredictionResult(finalResultUrl);
                exec.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.COMPLETED).orElseThrow());
                modelExecutionRepository.saveAndFlush(exec);
            });

            // 12. Complete async task
            final Integer finalExecutionId = execution.getId();
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.executeWithoutResult(status -> {
                if (taskStatusService.stopRequested(taskId)) {
                    return;
                }
                AsyncTaskStatus freshTask = taskStatusRepository.findById(taskId).orElseThrow();
                freshTask.setStatus(TaskStatusEnum.COMPLETED);
                freshTask.setFinishedAt(ZonedDateTime.now());
                freshTask.setExecutionId(finalExecutionId);
                taskStatusRepository.saveAndFlush(freshTask);
            });

            log.info("✅ Weka container prediction complete [taskId={}]. Output at: {}", taskId, resultUrl);

        } catch (UserInitiatedStopException e) {
            log.warn("🛑 Weka prediction manually stopped by user [taskId={}]: {}", taskId, e.getMessage());

            taskStatusService.taskStoppedExecution(taskId, execution != null ? execution.getId() : null);

            ModelExecutionStatusEnum finalStatus = complete
                    ? ModelExecutionStatusEnum.COMPLETED
                    : ModelExecutionStatusEnum.FAILED;

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
            log.error("❌ Weka prediction failed [taskId={}]: {}", taskId, e.getMessage(), e);

            taskStatusService.taskFailed(taskId, e.getMessage());

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

            throw new RuntimeException("Weka prediction failed: " + e.getMessage(), e);

        } finally {
            try {
                if (System.getenv("PRESERVE_SHARED_DEBUG") == null) {
                    if (dataDir != null) FileUtils.deleteDirectory(dataDir.toFile());
                    if (outputDir != null) FileUtils.deleteDirectory(outputDir.toFile());
                }
            } catch (IOException cleanupEx) {
                log.warn("⚠️ Failed to clean up temp directories: {}", cleanupEx.getMessage());
            }
        }
    }

    private void setDirectoryPermissions(Path dir) {
        try {
            java.nio.file.attribute.PosixFileAttributeView view = Files.getFileAttributeView(
                    dir, java.nio.file.attribute.PosixFileAttributeView.class);
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
