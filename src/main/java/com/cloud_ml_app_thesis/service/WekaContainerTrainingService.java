package com.cloud_ml_app_thesis.service;

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
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.config.PathResolver;
import com.cloud_ml_app_thesis.dto.train.WekaContainerTrainMetadata;
import com.cloud_ml_app_thesis.entity.Algorithm;
import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.entity.ModelType;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.repository.AlgorithmTypeRepository;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.exception.UserInitiatedStopException;
import com.cloud_ml_app_thesis.repository.AlgorithmConfigurationRepository;
import com.cloud_ml_app_thesis.repository.DatasetConfigurationRepository;
import com.cloud_ml_app_thesis.repository.ModelTypeRepository;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.TrainingRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.util.ContainerRunner;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for running Weka (predefined) algorithm training as containerized Kubernetes jobs.
 * Similar to CustomTrainingService but for Weka algorithms.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WekaContainerTrainingService {

    private final BucketResolver bucketResolver;
    private final MinioService minioService;
    private final TrainingRepository trainingRepository;
    private final ModelService modelService;
    private final ContainerRunner containerRunner;
    private final TrainingStatusRepository trainingStatusRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final ModelTypeRepository modelTypeRepository;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final AlgorithmConfigurationRepository algorithmConfigurationRepository;
    private final AlgorithmTypeRepository algorithmTypeRepository;
    private final ModelRepository modelRepository;
    private final TaskStatusService taskStatusService;
    private final PathResolver pathResolver;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    private static final String WEKA_RUNNER_IMAGE = "thesisapp/weka-runner:latest";

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void trainWeka(String taskId, UUID userId, String username, WekaContainerTrainMetadata metadata) {
        boolean complete = false;
        Model model = null;
        Integer trainingId = metadata.trainingId();

        log.info("🧠 Starting Weka container training [taskId={}] for user={}", taskId, username);
        log.info("🧪 SHARED_VOLUME: {}", pathResolver.getSharedPathRoot());

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Async task tracking not found"));

        Path dataDir = null;
        Path outputDir = null;
        String uploadedModelKey = null;
        String uploadedMetricsKey = null;
        String modelBucket = null;
        String metricsBucket = null;

        try {
            // 1. Update task to RUNNING
            task.setStatus(TaskStatusEnum.RUNNING);
            taskStatusRepository.save(task);

            // 2. Load configurations
            Training training = trainingRepository.findById(trainingId)
                    .orElseThrow(() -> new EntityNotFoundException("Training not found"));

            DatasetConfiguration datasetConfig = datasetConfigurationRepository.findById(metadata.datasetConfigurationId())
                    .orElseThrow(() -> new EntityNotFoundException("DatasetConfiguration not found"));

            AlgorithmConfiguration algorithmConfig = algorithmConfigurationRepository.findById(metadata.algorithmConfigurationId())
                    .orElseThrow(() -> new EntityNotFoundException("AlgorithmConfiguration not found"));

            Algorithm algorithm = algorithmConfig.getAlgorithm();

            // Update training status to RUNNING
            training.setStartedDate(ZonedDateTime.now());
            training.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.RUNNING)
                    .orElseThrow(() -> new EntityNotFoundException("TrainingStatus RUNNING not found")));
            training = trainingRepository.save(training);
            entityManager.detach(training);

            // Check for stop request
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop for task " + taskId);
            }

            // 3. Download dataset to shared volume
            Path datasetPath = minioService.downloadObjectToTempFile(
                    metadata.datasetBucket(), metadata.datasetKey());
            log.info("📥 Dataset [{}] downloaded to: {}", metadata.datasetKey(), datasetPath);

            // 4. Prepare /data & /model directories
            Path basePath = pathResolver.getSharedPathRoot();
            log.info("📂 Base shared path: {}", basePath);

            dataDir = Files.createTempDirectory(basePath, "weka-training-ds-");
            outputDir = Files.createTempDirectory(basePath, "weka-training-out-");
            setDirectoryPermissions(dataDir);
            setDirectoryPermissions(outputDir);

            log.info("📁 Training paths: dataDir={}, outputDir={}", dataDir, outputDir);

            // 5. Copy dataset to data directory
            Path datasetInside = dataDir.resolve("dataset.csv");

            // Check if the dataset is Excel and convert to CSV if needed
            String datasetFileName = datasetPath.getFileName().toString().toLowerCase();
            if (datasetFileName.endsWith(".xls") || datasetFileName.endsWith(".xlsx")) {
                log.info("📊 Detected Excel file, converting to CSV...");
                String csvPath = com.cloud_ml_app_thesis.util.XlsToCsv.convertExcelToCsv(
                        Files.newInputStream(datasetPath), datasetFileName);
                Files.copy(Path.of(csvPath), datasetInside, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(Path.of(csvPath));
            } else {
                Files.copy(datasetPath, datasetInside, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("✅ Copied dataset.csv");

            // 6. Create params.json with algorithm info
            Map<String, Object> params = new HashMap<>();
            params.put("algorithmClassName", algorithm.getClassName());
            params.put("algorithmType", algorithm.getType().getName().name());
            params.put("options", algorithmConfig.getOptions() != null ? algorithmConfig.getOptions() : "");
            params.put("targetColumn", metadata.targetColumn());
            params.put("basicAttributesColumns", metadata.basicAttributesColumns());

            Path paramsFile = dataDir.resolve("params.json");
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter().writeValue(paramsFile.toFile(), params);
            log.info("✅ Created params.json: {}", params);

            // 7. Check for stop request before running container
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop before Weka training for task " + taskId);
            }

            // 8. Run Weka training container with callback to store jobName for cancellation support
            log.info("🚀 Running Weka training container...");
            containerRunner.runWekaTrainingContainer(WEKA_RUNNER_IMAGE, dataDir, outputDir,
                    jobName -> taskStatusService.updateJobName(taskId, jobName));

            // 9. Read output files
            File modelFile = Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".ser"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No model file (.ser) generated", null));

            File metricsFile = Files.walk(outputDir)
                    .filter(p -> p.getFileName().toString().equals("metrics.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No metrics file generated", null));

            log.info("✅ Found output files: model={}, metrics={}", modelFile.getName(), metricsFile.getName());

            // 10. Upload results to MinIO
            String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
            String modelFolder = username + "_" + timestamp + "/";

            String modelKey = modelFolder + modelFile.getName();
            String metricsKey = username + "_" + timestamp + "_" + metricsFile.getName();
            modelBucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
            metricsBucket = bucketResolver.resolve(BucketTypeEnum.METRICS);

            try (InputStream modelIn = new FileInputStream(modelFile);
                 InputStream metricsIn = new FileInputStream(metricsFile)) {
                minioService.uploadToMinio(modelIn, modelBucket, modelKey, modelFile.length(), "application/octet-stream");
                uploadedModelKey = modelKey;  // Track for cleanup on stop
                minioService.uploadToMinio(metricsIn, metricsBucket, metricsKey, metricsFile.length(), "application/json");
                uploadedMetricsKey = metricsKey;  // Track for cleanup on stop
            }

            log.info("☁️ Model uploaded to MinIO: {}/{}", modelBucket, modelKey);
            log.info("☁️ Metrics uploaded to MinIO: {}/{}", metricsBucket, metricsKey);

            String modelUrl = modelService.generateMinioUrl(modelBucket, modelKey);
            String metricsUrl = modelService.generateMinioUrl(metricsBucket, metricsKey);

            // 11. Save model entity
            ModelType predefinedType = modelTypeRepository.findByName(ModelTypeEnum.PREDEFINED)
                    .orElseThrow(() -> new EntityNotFoundException("ModelType PREDEFINED not found"));

            modelService.saveModel(training, modelUrl, metricsUrl, predefinedType);

            // 12. Set algorithmType on AlgorithmConfiguration (required for visualization)
            AlgorithmTypeEnum algTypeEnum = algorithm.getType().getName();
            AlgorithmType algType = algorithmTypeRepository.findByName(algTypeEnum)
                    .orElseThrow(() -> new EntityNotFoundException("AlgorithmType not found: " + algTypeEnum));
            algorithmConfig.setAlgorithmType(algType);
            algorithmConfigurationRepository.save(algorithmConfig);
            log.info("✅ Set algorithmType={} on AlgorithmConfiguration", algTypeEnum);

            complete = true;

            // Check for stop request
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop after model upload for task " + taskId);
            }

            model = modelRepository.findByTraining(training)
                    .orElseThrow(() -> new EntityNotFoundException("Model not linked to training"));

            // 12. Update Training record
            Integer finalTrainingId = trainingId;
            Model finalModel = model;
            Path finalMetricsFile = metricsFile.toPath();
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.executeWithoutResult(status -> {
                Training tr = trainingRepository.findById(finalTrainingId)
                        .orElseThrow(() -> new EntityNotFoundException("Training not found"));
                tr.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.COMPLETED)
                        .orElseThrow(() -> new EntityNotFoundException("TrainingStatus COMPLETED not found")));
                tr.setFinishedDate(ZonedDateTime.now());
                try {
                    tr.setResults(Files.readString(finalMetricsFile));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read metrics file", e);
                }
                tr.setModel(finalModel);
                trainingRepository.saveAndFlush(tr);
            });

            // 13. Complete async task
            Model finalModel1 = model;
            Integer finalTrainingId1 = trainingId;
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.executeWithoutResult(status -> {
                AsyncTaskStatus freshTask = taskStatusRepository.findById(taskId)
                        .orElseThrow(() -> new IllegalStateException("Task not found"));
                if (freshTask.getStatus() != TaskStatusEnum.STOPPED) {
                    freshTask.setStatus(TaskStatusEnum.COMPLETED);
                    freshTask.setFinishedAt(ZonedDateTime.now());
                    freshTask.setModelId(finalModel1.getId());
                    freshTask.setTrainingId(finalTrainingId1);
                    taskStatusRepository.saveAndFlush(freshTask);
                }
            });

            log.info("✅ Weka container training complete [taskId={}] with modelId={}", taskId, model.getId());

        } catch (UserInitiatedStopException e) {
            log.warn("🛑 Training manually stopped by user [taskId={}]: {}", taskId, e.getMessage());

            // Cleanup MinIO files if they were uploaded
            if (uploadedModelKey != null && modelBucket != null) {
                try {
                    minioService.deleteObject(modelBucket, uploadedModelKey);
                    log.info("🧹 Cleaned up model file from MinIO: {}/{}", modelBucket, uploadedModelKey);
                } catch (Exception cleanupEx) {
                    log.warn("⚠️ Failed to clean up model from MinIO: {}", cleanupEx.getMessage());
                }
            }
            if (uploadedMetricsKey != null && metricsBucket != null) {
                try {
                    minioService.deleteObject(metricsBucket, uploadedMetricsKey);
                    log.info("🧹 Cleaned up metrics file from MinIO: {}/{}", metricsBucket, uploadedMetricsKey);
                } catch (Exception cleanupEx) {
                    log.warn("⚠️ Failed to clean up metrics from MinIO: {}", cleanupEx.getMessage());
                }
            }

            taskStatusService.taskStoppedTraining(taskId, trainingId, model != null ? model.getId() : null);

            final Integer tid = trainingId;
            final Model finalModel = model;
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.executeWithoutResult(status -> {
                Training tr = trainingRepository.findById(tid)
                        .orElseThrow(() -> new EntityNotFoundException("Training not found"));
                tr.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                        .orElseThrow(() -> new EntityNotFoundException("TrainingStatus not found")));
                if (finalModel != null) {
                    tr.setModel(finalModel);
                }
                tr.setFinishedDate(ZonedDateTime.now());
                trainingRepository.saveAndFlush(tr);
            });

        } catch (Exception e) {
            // Check if this was actually a user-initiated stop (job cancelled)
            if (taskStatusService.stopRequested(taskId)) {
                log.warn("🛑 Training stopped due to job cancellation [taskId={}]: {}", taskId, e.getMessage());

                // Cleanup MinIO files if they were uploaded
                if (uploadedModelKey != null && modelBucket != null) {
                    try {
                        minioService.deleteObject(modelBucket, uploadedModelKey);
                        log.info("🧹 Cleaned up model file from MinIO: {}/{}", modelBucket, uploadedModelKey);
                    } catch (Exception cleanupEx) {
                        log.warn("⚠️ Failed to clean up model from MinIO: {}", cleanupEx.getMessage());
                    }
                }
                if (uploadedMetricsKey != null && metricsBucket != null) {
                    try {
                        minioService.deleteObject(metricsBucket, uploadedMetricsKey);
                        log.info("🧹 Cleaned up metrics file from MinIO: {}/{}", metricsBucket, uploadedMetricsKey);
                    } catch (Exception cleanupEx) {
                        log.warn("⚠️ Failed to clean up metrics from MinIO: {}", cleanupEx.getMessage());
                    }
                }

                taskStatusService.taskStoppedTraining(taskId, trainingId, model != null ? model.getId() : null);

                final Integer tid = trainingId;
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.executeWithoutResult(status -> {
                    Training tr = trainingRepository.findById(tid)
                            .orElseThrow(() -> new EntityNotFoundException("Training not found"));
                    tr.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                            .orElseThrow(() -> new EntityNotFoundException("TrainingStatus not found")));
                    tr.setFinishedDate(ZonedDateTime.now());
                    trainingRepository.saveAndFlush(tr);
                });
                return; // Exit gracefully for user-initiated stop
            }

            log.error("❌ Weka training failed [taskId={}]: {}", taskId, e.getMessage(), e);

            final Integer tid = trainingId;
            if (tid != null) {
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.executeWithoutResult(status -> {
                    Training tr = trainingRepository.findById(tid)
                            .orElseThrow(() -> new EntityNotFoundException("Training not found"));
                    tr.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                            .orElseThrow(() -> new EntityNotFoundException("TrainingStatus FAILED not found")));
                    tr.setResults(e.getMessage());
                    tr.setFinishedDate(ZonedDateTime.now());
                    trainingRepository.saveAndFlush(tr);
                });
            }

            taskStatusService.taskFailed(taskId, e.getMessage());
            throw new RuntimeException("Weka container training failed", e);

        } finally {
            // Cleanup temp directories
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
