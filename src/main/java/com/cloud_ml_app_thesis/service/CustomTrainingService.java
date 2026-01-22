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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.config.PathResolver;
import com.cloud_ml_app_thesis.dto.train.CustomTrainMetadata;
import com.cloud_ml_app_thesis.entity.AlgorithmParameter;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.entity.CustomAlgorithm;
import com.cloud_ml_app_thesis.entity.CustomAlgorithmConfiguration;
import com.cloud_ml_app_thesis.entity.CustomAlgorithmImage;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.entity.ModelType;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.exception.UserInitiatedStopException;
import com.cloud_ml_app_thesis.repository.AlgorithmConfigurationRepository;
import com.cloud_ml_app_thesis.repository.AlgorithmImageRepository;
import com.cloud_ml_app_thesis.repository.CustomAlgorithmConfigurationRepository;
import com.cloud_ml_app_thesis.repository.CustomAlgorithmRepository;
import com.cloud_ml_app_thesis.repository.DatasetConfigurationRepository;
import com.cloud_ml_app_thesis.repository.ModelTypeRepository;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.TrainingRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.util.ContainerRunner;
import com.cloud_ml_app_thesis.util.FileUtil;
import com.nimbusds.oauth2.sdk.util.StringUtils;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CustomTrainingService {

    private final CustomAlgorithmRepository customAlgorithmRepository;
    private final BucketResolver bucketResolver;
    private final MinioService minioService;
    private final TrainingRepository trainingRepository;
    private final ModelService modelService;
    private final ContainerRunner containerRunner;
    private final TrainingStatusRepository trainingStatusRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final ModelTypeRepository modelTypeRepository;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final ModelRepository modelRepository;
    private final AlgorithmConfigurationRepository algorithmConfigurationRepository;
    private final CustomAlgorithmConfigurationRepository customAlgorithmConfigurationRepository;
    private final AlgorithmImageRepository algorithmImageRepository;
    private final TaskStatusService taskStatusService;
    private final PathResolver pathResolver;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void trainCustom(String taskId, UUID userId, String username, CustomTrainMetadata metadata) {
        boolean complete = false;
        Model model = null;
        Integer trainingId = null;

        log.info("🧠 Starting training [taskId={}] for user={}", taskId, username);

        System.out.println("🧪 SHARED_VOLUME from env: " + pathResolver.getSharedPathRoot());
        System.out.println("🧪 TMPDIR from env: " + pathResolver.getTmpDir());

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Async task tracking not found"));

        Path dataDir = null;
        Path outputDir = null;
        String uploadedModelKey = null;
        String uploadedMetricsKey = null;
        String uploadedLabelMappingKey = null;
        String uploadedFeatureColumnsKey = null;
        String modelBucket = null;
        String metricsBucket = null;

        Training training = null;

        try {
            // 1. Validate access - fetch algorithm with images in a separate transaction
            CustomAlgorithm algorithm;
            CustomAlgorithmImage activeImage;

            // Fetch algorithm and active image in a read-only transaction
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.setReadOnly(true);
            algorithm = transactionTemplate.execute(status -> {
                CustomAlgorithm alg = customAlgorithmRepository.findWithOwnerById(metadata.algorithmId())
                        .orElseThrow(() -> new IllegalStateException("Custom algorithm not found"));
                // Force initialization of both collections
                alg.getImages().size();
                alg.getParameters().size();
                return alg;
            });

            // Reset transaction template settings
            transactionTemplate.setReadOnly(false);

            activeImage = algorithm.getImages().stream()
                    .filter(CustomAlgorithmImage::isActive)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No active image found"));

            TrainingStatus running = trainingStatusRepository.findByName(TrainingStatusEnum.RUNNING)
                    .orElseThrow(() -> new IllegalStateException("TrainingStatus RUNNING not found"));

            DatasetConfiguration datasetConfig = datasetConfigurationRepository.findById(metadata.datasetConfigurationId())
                    .orElseThrow(() -> new IllegalStateException("DatasetConfiguration not found"));

            // Check if Training was pre-created (retrain mode) or needs to be created (fresh training)
            if (metadata.trainingId() != null) {
                // Retrain mode: use pre-created Training
                training = trainingRepository.findById(metadata.trainingId())
                        .orElseThrow(() -> new IllegalStateException("Training not found: " + metadata.trainingId()));
                training.setStartedDate(ZonedDateTime.now());
                training.setStatus(running);
                training = trainingRepository.save(training);
                log.info("🔁 Using pre-created Training for retrain: trainingId={}", training.getId());
            } else {
                // Fresh training: create new Training
                training = new Training();
                training.setDatasetConfiguration(datasetConfig);
                training.setStartedDate(ZonedDateTime.now());
                training.setStatus(running);
                training.setUser(entityManager.getReference(User.class, userId));
                training = trainingRepository.save(training);
            }
            trainingId = training.getId();
            entityManager.detach(training);
            log.info("⏳ Sleeping for 10 seconds before starting training (for manual stop testing)");
            Thread.sleep(10000);
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop for task " + taskId);
            }
            if (!algorithm.getAccessibility().getName().equals(AlgorithmAccessibiltyEnum.PUBLIC)
                    && !algorithm.getOwner().getId().equals(userId)) {
                task.setStatus(TaskStatusEnum.FAILED);
                task.setErrorMessage("Not authorized");
                taskStatusRepository.save(task);
                throw new AccessDeniedException("User not authorized to train this algorithm");
            }
            // 2. Load dataset & configuration
            Path datasetPath = minioService.downloadObjectToTempFile(
                    metadata.datasetBucket(), metadata.datasetKey());
            log.info("📥 Dataset [{}] downloaded to: {}", metadata.datasetKey(), datasetPath);
            try {
                List<String> previewLines = Files.readAllLines(datasetPath)
                        .stream()
                        .limit(5)
                        .collect(Collectors.toList());

                log.info("📄 Preview of training dataset (first 5 lines):");
                previewLines.forEach(line -> log.info("    → {}", line));
            } catch (IOException e) {
                log.warn("⚠️ Could not read preview of dataset at {}: {}", datasetPath, e.getMessage());
            }


            String dockerImageTag;
            if (activeImage.getDockerTarKey() != null) {
                // Load TAR image - first download to /tmp
                Path tarPathTemp = minioService.downloadObjectToTempFile(
                        bucketResolver.resolve(BucketTypeEnum.CUSTOM_ALGORITHM),
                        activeImage.getDockerTarKey()
                );
                log.info("📦 TAR image downloaded from MinIO to temp: {}", tarPathTemp);

                // Copy TAR to shared volume so loader pod can access it
                Path sharedRoot = pathResolver.getSharedPathRoot();
                Path tarPathShared = sharedRoot.resolve(tarPathTemp.getFileName());
                Files.copy(tarPathTemp, tarPathShared, StandardCopyOption.REPLACE_EXISTING);
                log.info("📋 TAR image copied to shared volume: {}", tarPathShared);

                // Clean up temp file
                Files.deleteIfExists(tarPathTemp);

                // Docker tags must be lowercase
                dockerImageTag = username.toLowerCase() + "/" + algorithm.getName().toLowerCase() + ":" + activeImage.getVersion().toLowerCase();
                log.info("TAR image tag: {}", dockerImageTag);
                containerRunner.loadImageFromTar(tarPathShared, dockerImageTag);
            } else if (StringUtils.isNotBlank(activeImage.getDockerHubUrl())) {
                dockerImageTag = activeImage.getDockerHubUrl();
                log.info("Docker Image with docker hub tag: {}", dockerImageTag);
                containerRunner.pullImage(dockerImageTag);
            } else {
                task.setStatus(TaskStatusEnum.FAILED);
                task.setErrorMessage("No image source found");
                taskStatusRepository.save(task);
                throw new IllegalStateException("Active image has neither dockerTarKey nor dockerHubUrl");
            }

            activeImage.setName(dockerImageTag);
            algorithmImageRepository.save(activeImage);

            //Loading Parameters File from Minio
            Map<String, Object> defaultParams = FileUtil.convertDefaults(algorithm.getParameters());
            Map<String, Object> userParams = FileUtil.loadUserParamsFromMinio(minioService, metadata.paramsJsonKey(), metadata.paramsJsonBucket());
            Map<String, Object> finalParams = FileUtil.mergeParams(defaultParams, userParams);

            CustomAlgorithmConfiguration algoConfig = new CustomAlgorithmConfiguration();
            algoConfig.setAlgorithm(algorithm);
            User userRef = new User();
            userRef.setId(userId);
            algoConfig.setUser(userRef);
            CustomAlgorithmConfiguration savedConfig = customAlgorithmConfigurationRepository.save(algoConfig);

            log.info("🧩 Final training parameters (merged):");
            finalParams.forEach((key, value) -> log.info("  • {} = {} ({})", key, value, value != null ? value.getClass().getSimpleName() : "null"));

            List<AlgorithmParameter> configParams = finalParams.entrySet().stream()
                    .map(entry -> {
                        AlgorithmParameter param = new AlgorithmParameter();
                        param.setName(entry.getKey());
                        param.setValue(entry.getValue().toString());
                        param.setType(entry.getValue().getClass().getSimpleName()); // π.χ. Integer, Boolean
                        param.setConfiguration(algoConfig);
                        return param;
                    })
                    .collect(Collectors.toList());

            savedConfig.setParameters(configParams);
            customAlgorithmConfigurationRepository.save(savedConfig);

            customAlgorithmConfigurationRepository.flush();

            boolean alreadyTrained = trainingRepository
                    .existsByCustomAlgorithmConfiguration_Algorithm_IdAndDatasetConfiguration_Id(algoConfig.getId(), datasetConfig.getId());

            if (alreadyTrained) {
                log.warn("🚫 Training already exists for algorithm={} and datasetConfiguration={}",
                        algorithm.getId(), datasetConfig.getId());
                task.setStatus(TaskStatusEnum.FAILED);
                task.setErrorMessage("Task already trained");
                taskStatusRepository.save(task);
                throw new IllegalStateException("This algorithm is already trained on the same dataset");
            }

            log.info("Prepare training data...");
             //5. Prepare /data & /model directories
            //Path basePath = Paths.get(System.getenv("SHARED_VOLUME")).toAbsolutePath();
            Path basePath = pathResolver.getSharedPathRoot();
            log.info("📂 Base shared path: {}", basePath);

            dataDir = Files.createTempDirectory(basePath, "training-ds-");
            outputDir = Files.createTempDirectory(basePath, "training-out-");
            setDirectoryPermissions(dataDir);
            setDirectoryPermissions(outputDir);
            log.info("Dataset path: {}", dataDir);
            log.info("Output path: {}", outputDir);
            Files.createDirectories(dataDir);   // Δημιουργεί τον φάκελο με force αν δεν υπάρχει
            Files.createDirectories(outputDir);
            log.info("📌 Real host data path: {}", dataDir.toAbsolutePath());
            log.info("📌 Does it exist? {}", Files.exists(dataDir.resolve("dataset.csv")));
            FileUtil.writeParamsJson(finalParams, datasetConfig, dataDir);

            Path datasetInside = dataDir.resolve("dataset.csv");

            // Check if the dataset is an Excel file and convert to CSV if needed
            String datasetFileName = datasetPath.getFileName().toString().toLowerCase();
            if (datasetFileName.endsWith(".xls") || datasetFileName.endsWith(".xlsx")) {
                log.info("📊 Detected Excel file, converting to CSV for Docker container...");
                String csvPath = com.cloud_ml_app_thesis.util.XlsToCsv.convertExcelToCsv(
                    Files.newInputStream(datasetPath),
                    datasetFileName
                );
                Files.copy(Path.of(csvPath), datasetInside, StandardCopyOption.REPLACE_EXISTING);
                // Clean up temp CSV file
                Files.deleteIfExists(Path.of(csvPath));
            } else {
                Files.copy(datasetPath, datasetInside, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!Files.exists(datasetInside)) {
                throw new IllegalStateException("❌ dataset.csv does not exist before starting container!");
            }

            // Copy standardized train.py template to data directory
            try (InputStream trainTemplate = getClass().getResourceAsStream("/templates/train.py")) {
                if (trainTemplate == null) {
                    throw new IllegalStateException("❌ train.py template not found in resources");
                }
                Path trainPyPath = dataDir.resolve("train.py");
                Files.copy(trainTemplate, trainPyPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("✅ Copied standardized train.py template to {}", trainPyPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy train.py template", e);
            }

            // Extract algorithm.py from the user's Docker image to /data directory
            try {
                containerRunner.copyFileFromImage(dockerImageTag, "/app/algorithm.py", dataDir.resolve("algorithm.py"));
                log.info("✅ Extracted algorithm.py from Docker image to /data");
            } catch (Exception e) {
                log.error("❌ Failed to extract algorithm.py from Docker image: {}", e.getMessage());
                throw new RuntimeException("Could not extract algorithm.py from user's Docker image", e);
            }
            Thread.sleep(1000);
            log.info("Copying dataset from {} to {}", datasetPath, datasetInside);
            log.info("✅ Copied dataset.csv");
            log.info("📁 Training paths: /data={}, /model={}", dataDir, outputDir);
            try (java.util.stream.Stream<Path> stream = java.nio.file.Files.list(dataDir)) {
                stream.forEach(p -> log.info("📄 dataDir contains: {}", p));
            } catch (IOException e) {
                log.warn("⚠️ Could not list dataDir: {}", e.getMessage());
            }

            File datasetInsideFile = dataDir.resolve("dataset.csv").toFile();
            log.info("🔍 dataset.csv exists: {}", datasetInsideFile.exists());
            log.info("🔍 dataset.csv absolute path: {}", datasetInsideFile.getAbsolutePath());
            log.info("🔍 dataset.csv length: {}", datasetInsideFile.length());

            log.info("📂 Validating that dataset is visible to Docker (host path): {}", dataDir);
            log.info("🧪 Host file exists: {}", Files.exists(dataDir.resolve("dataset.csv")));

            // 6. Check for stop request before running training container
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop before Docker training for task " + taskId);
            }
            // Run training container with callback to store jobName for cancellation support
            containerRunner.runTrainingContainer(dockerImageTag, dataDir, outputDir,
                    jobName -> taskStatusService.updateJobName(taskId, jobName));

            log.info("Copying dataset from {} to {}", datasetPath, datasetInside);
            log.info("✅ Copied dataset.csv");
            // 7. Read output files
            File modelFile = Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".pkl"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No model file (.pkl) generated", null));

            File metricsFile = Files.walk(outputDir)
                    .filter(p -> p.getFileName().toString().equals("metrics.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No metrics file generated", null));

            // Find optional JSON artifact files (label_mapping.json, feature_columns.json)
            File labelMappingFile = Files.walk(outputDir)
                    .filter(p -> p.getFileName().toString().equals("label_mapping.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);

            File featureColumnsFile = Files.walk(outputDir)
                    .filter(p -> p.getFileName().toString().equals("feature_columns.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);

            log.info("✅ model: {}, metrics: {}, label_mapping: {}, feature_columns: {}",
                    modelFile.getName(), metricsFile.getName(),
                    labelMappingFile != null ? labelMappingFile.getName() : "none",
                    featureColumnsFile != null ? featureColumnsFile.getName() : "none");

            String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());

            // 8. Upload results to MinIO using folder structure
            // Create a unique folder for this model: username_timestamp/
            String modelFolder = username + "_" + timestamp + "/";

            String modelKey = modelFolder + modelFile.getName();
            String metricsKey = username + "_" + timestamp + "_" + metricsFile.getName();  // Metrics stays flat
            modelBucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
            metricsBucket = bucketResolver.resolve(BucketTypeEnum.METRICS);

            try (InputStream in = new FileInputStream(modelFile);
                 InputStream metricsIn = new FileInputStream(metricsFile)) {
                minioService.uploadToMinio(in, modelBucket, modelKey, modelFile.length(), "application/octet-stream");
                uploadedModelKey = modelKey;  // Track for cleanup on stop
                minioService.uploadToMinio(metricsIn, metricsBucket, metricsKey, metricsFile.length(), "application/json");
                uploadedMetricsKey = metricsKey;  // Track for cleanup on stop
            }

            log.info("Model uploaded to MinIO folder: {}/{}", modelBucket, modelFolder);
            log.info("Metrics uploaded to MinIO: {}", bucketResolver.resolve(BucketTypeEnum.METRICS));

            // Upload optional JSON artifacts to the same model folder
            String labelMappingUrl = null;
            String featureColumnsUrl = null;

            if (labelMappingFile != null) {
                String labelMappingKey = modelFolder + labelMappingFile.getName();
                try (InputStream labelMappingIn = new FileInputStream(labelMappingFile)) {
                    minioService.uploadToMinio(labelMappingIn, modelBucket, labelMappingKey, labelMappingFile.length(), "application/json");
                    uploadedLabelMappingKey = labelMappingKey;  // Track for cleanup on stop
                    labelMappingUrl = modelService.generateMinioUrl(modelBucket, labelMappingKey);
                    log.info("Label mapping uploaded to: {}/{}", modelBucket, labelMappingKey);
                }
            }

            if (featureColumnsFile != null) {
                String featureColumnsKey = modelFolder + featureColumnsFile.getName();
                try (InputStream featureColumnsIn = new FileInputStream(featureColumnsFile)) {
                    minioService.uploadToMinio(featureColumnsIn, modelBucket, featureColumnsKey, featureColumnsFile.length(), "application/json");
                    uploadedFeatureColumnsKey = featureColumnsKey;  // Track for cleanup on stop
                    featureColumnsUrl = modelService.generateMinioUrl(modelBucket, featureColumnsKey);
                    log.info("Feature columns uploaded to: {}/{}", modelBucket, featureColumnsKey);
                }
            }

            String modelUrl = modelService.generateMinioUrl(modelBucket, modelKey);
            String metricsUrl = modelService.generateMinioUrl(metricsBucket, metricsKey);

            // 10. Save model entity
            ModelType customType = modelTypeRepository.findByName(ModelTypeEnum.CUSTOM)
                    .orElseThrow(() -> new IllegalStateException("AlgorithmType CUSTOM not found"));

            modelService.saveModel(training, modelUrl, metricsUrl, labelMappingUrl, featureColumnsUrl, customType);

            log.info("⏳ Sleeping for 20 seconds after training (for manual stop testing)");
            complete=true;
            Thread.sleep(10000);
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop for task " + taskId);
            }
             model = modelRepository.findByTraining(training)
                    .orElseThrow(() -> new EntityNotFoundException("Model not linked to training"));

            // 9. Update Training record in separate transaction
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Integer finalTrainingId = trainingId;
            Model finalModel = model;
            CustomAlgorithmConfiguration finalSavedConfig = savedConfig;
            Path finalMetricsFile = metricsFile.toPath();
            transactionTemplate.executeWithoutResult(status -> {
                Training tr = trainingRepository.findById(finalTrainingId)
                        .orElseThrow(() -> new EntityNotFoundException("Training not found"));
                tr.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.COMPLETED)
                        .orElseThrow(() -> new EntityNotFoundException("TrainingStatus COMPLETED not found")));
                tr.setCustomAlgorithmConfiguration(finalSavedConfig);
                tr.setFinishedDate(ZonedDateTime.now());
                try {
                    tr.setResults(Files.readString(finalMetricsFile));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read metrics file", e);
                }
                tr.setModel(finalModel);
                trainingRepository.saveAndFlush(tr);
            });

            // 10. Complete async task with model and training info in separate transaction
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Model finalModel1 = model;
            Integer finalTrainingId1 = trainingId;
            transactionTemplate.executeWithoutResult(status -> {
                AsyncTaskStatus freshTask = taskStatusRepository.findById(taskId)
                        .orElseThrow(() -> new IllegalStateException("Task not found"));
                if (freshTask.getStatus() != TaskStatusEnum.STOPPED) {
                    // Do everything in one operation to avoid optimistic locking conflicts
                    freshTask.setStatus(TaskStatusEnum.COMPLETED);
                    freshTask.setFinishedAt(ZonedDateTime.now());
                    freshTask.setModelId(finalModel1.getId());
                    freshTask.setTrainingId(finalTrainingId1);
                    taskStatusRepository.saveAndFlush(freshTask);
                    log.info("Task completed [{}] with modelId={}, trainingId={}", taskId, finalModel1.getId(), finalTrainingId1);
                }
            });
            log.info("✅ Training complete [taskId={}] with modelId={}", taskId, model.getId());

        }catch(UserInitiatedStopException e) {
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
            if (uploadedLabelMappingKey != null && modelBucket != null) {
                try {
                    minioService.deleteObject(modelBucket, uploadedLabelMappingKey);
                    log.info("🧹 Cleaned up label mapping from MinIO: {}/{}", modelBucket, uploadedLabelMappingKey);
                } catch (Exception cleanupEx) {
                    log.warn("⚠️ Failed to clean up label mapping from MinIO: {}", cleanupEx.getMessage());
                }
            }
            if (uploadedFeatureColumnsKey != null && modelBucket != null) {
                try {
                    minioService.deleteObject(modelBucket, uploadedFeatureColumnsKey);
                    log.info("🧹 Cleaned up feature columns from MinIO: {}/{}", modelBucket, uploadedFeatureColumnsKey);
                } catch (Exception cleanupEx) {
                    log.warn("⚠️ Failed to clean up feature columns from MinIO: {}", cleanupEx.getMessage());
                }
            }

            taskStatusService.taskStoppedTraining(taskId, trainingId, model != null ? model.getId() : null);

            // User-initiated stop should always result in FAILED status
            TrainingStatusEnum finalStatus = TrainingStatusEnum.FAILED;

            // Update training status in separate transaction
            final Integer tid = trainingId;
            final Model finalModel = model;
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionTemplate.executeWithoutResult(status -> {
                Training tr = trainingRepository.findById(tid)
                        .orElseThrow(() -> new EntityNotFoundException("Training not found"));
                tr.setStatus(trainingStatusRepository.findByName(finalStatus)
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
                if (uploadedLabelMappingKey != null && modelBucket != null) {
                    try {
                        minioService.deleteObject(modelBucket, uploadedLabelMappingKey);
                        log.info("🧹 Cleaned up label mapping from MinIO: {}/{}", modelBucket, uploadedLabelMappingKey);
                    } catch (Exception cleanupEx) {
                        log.warn("⚠️ Failed to clean up label mapping from MinIO: {}", cleanupEx.getMessage());
                    }
                }
                if (uploadedFeatureColumnsKey != null && modelBucket != null) {
                    try {
                        minioService.deleteObject(modelBucket, uploadedFeatureColumnsKey);
                        log.info("🧹 Cleaned up feature columns from MinIO: {}/{}", modelBucket, uploadedFeatureColumnsKey);
                    } catch (Exception cleanupEx) {
                        log.warn("⚠️ Failed to clean up feature columns from MinIO: {}", cleanupEx.getMessage());
                    }
                }

                taskStatusService.taskStoppedTraining(taskId, trainingId, model != null ? model.getId() : null);

                final Integer tid = trainingId;
                if (tid != null) {
                    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    transactionTemplate.executeWithoutResult(status -> {
                        Training tr = trainingRepository.findById(tid)
                                .orElseThrow(() -> new EntityNotFoundException("Training not found"));
                        tr.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                                .orElseThrow(() -> new EntityNotFoundException("TrainingStatus not found")));
                        tr.setFinishedDate(ZonedDateTime.now());
                        trainingRepository.saveAndFlush(tr);
                    });
                }
                return; // Exit gracefully for user-initiated stop
            }

            log.error("❌ Training failed [taskId={}]: {}", taskId, e.getMessage(), e);

            // Only update Training entity, let taskStatusService handle the task
            final Integer tid = trainingId;
            if (tid != null) {
                // Training -> FAILED (REQUIRES_NEW + fresh read)
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

            // Task -> FAILED (this already handles trainingId attachment if needed)
            taskStatusService.taskFailed(taskId, e.getMessage());

            throw new RuntimeException("Custom training failed", e);
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
