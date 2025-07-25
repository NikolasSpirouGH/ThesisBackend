package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.train.CustomTrainMetadata;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.ModelType;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.ModelTypeRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.util.DockerContainerRunner;
import com.cloud_ml_app_thesis.util.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final DockerContainerRunner dockerCommandRunner;
    private final TrainingStatusRepository trainingStatusRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final ModelTypeRepository modelTypeRepository;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final ModelRepository modelRepository;
    private final AlgorithmConfigurationRepository algorithmConfigurationRepository;
    private final CustomAlgorithmConfigurationRepository customAlgorithmConfigurationRepository;
    private final AlgorithmImageRepository algorithmImageRepository;
    private final TaskStatusService taskStatusService;
    ObjectMapper mapper = new ObjectMapper();


    @Transactional
    public void trainCustom(String taskId, User user, CustomTrainMetadata metadata) {
        log.info("🧠 Starting training [taskId={}] for user={}", taskId, user.getUsername());

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Async task tracking not found"));
        task.setStatus(TaskStatusEnum.RUNNING);
        taskStatusRepository.save(task);

        Path dataDir = null;
        Path outputDir = null;

        Training training ;
        
        try {
            // 1. Validate access
            CustomAlgorithm algorithm = customAlgorithmRepository.findById(metadata.algorithmId()).orElseThrow(() -> new IllegalStateException("Custom algorithm not found"));

            TrainingStatus running = trainingStatusRepository.findByName(TrainingStatusEnum.RUNNING)
                    .orElseThrow(() -> new IllegalStateException("TrainingStatus RUNNING not found"));
            training = new Training();
            training.setStartedDate(ZonedDateTime.now());
            training.setStatus(running);
            trainingRepository.save(training);

            if (!algorithm.getAccessibility().getName().equals(AlgorithmAccessibiltyEnum.PUBLIC)
                    && !algorithm.getOwner().getUsername().equals(user.getUsername())) {
                task.setStatus(TaskStatusEnum.FAILED);
                task.setErrorMessage("Not authorized");
                taskStatusRepository.save(task);
                throw new AccessDeniedException("User not authorized to train this algorithm");
            }
            // 2. Load dataset & configuration
            Path datasetPath = minioService.downloadObjectToTempFile(
                    metadata.datasetBucket(), metadata.datasetKey());
            log.info("📥 Dataset [{}] downloaded to: {}", metadata.datasetKey(), datasetPath);

            DatasetConfiguration datasetConfig = datasetConfigurationRepository.findById(metadata.datasetConfigurationId())
                    .orElseThrow(() -> new IllegalStateException("DatasetConfiguration not found"));

            CustomAlgorithmImage activeImage = algorithm.getImages().stream()
                    .filter(CustomAlgorithmImage::isActive)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No active image found"));

            String dockerImageTag;
            if (activeImage.getDockerTarKey() != null) {
                // Load TAR image
                Path tarPath = minioService.downloadObjectToTempFile(
                        bucketResolver.resolve(BucketTypeEnum.CUSTOM_ALGORITHM),
                        activeImage.getDockerTarKey()
                );
                log.info("📦 TAR image downloaded from MinIO: {}", tarPath);

                dockerImageTag = user.getUsername() + "/" + algorithm.getName() + ":" + activeImage.getVersion();
                log.info("TAR image tag: {}", dockerImageTag);
                dockerCommandRunner.loadDockerImageFromTar(tarPath, dockerImageTag);
            } else if (StringUtils.isNotBlank(activeImage.getDockerHubUrl())) {
                dockerImageTag = activeImage.getDockerHubUrl();
                log.info("Docker Image with docker hub tag: {}", dockerImageTag);
                dockerCommandRunner.pullDockerImage(dockerImageTag);
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

            // 5. Prepare /data & /model directories
            Path basePath = FileUtil.getSharedPathRoot();
            dataDir = Files.createTempDirectory(basePath, "training-ds-");
            outputDir = Files.createTempDirectory(basePath, "training-out-");


            FileUtil.writeParamsJson(finalParams, datasetConfig, dataDir);

            Path datasetInside = dataDir.resolve("dataset.csv");
            Files.copy(datasetPath, datasetInside, StandardCopyOption.REPLACE_EXISTING);

            log.info("📁 Training paths: /data={}, /model={}", dataDir, outputDir);

            // 6. Run training container
            dockerCommandRunner.runTrainingContainer(dockerImageTag, dataDir, outputDir);

            // 7. Read output files
            File modelFile = Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("metrics.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No model file generated", null));

            File metricsFile = Files.walk(outputDir)
                    .filter(p -> p.getFileName().toString().equals("metrics.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No metrics file generated", null));

            log.info("✅ model: {}, metrics: {}", modelFile.getName(), metricsFile.getName());

            String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());

            // 8. Upload results to MinIO
            String modelKey = user.getUsername() + "_" + timestamp + "_" + modelFile.getName();
            String metricsKey = user.getUsername() + "_" + timestamp + "_" + metricsFile.getName();
            String modelBucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
            String metricsBucket = bucketResolver.resolve(BucketTypeEnum.METRICS);

            try (InputStream in = new FileInputStream(modelFile);
                 InputStream metricsIn = new FileInputStream(metricsFile)) {
                minioService.uploadToMinio(in, modelBucket, modelKey, modelFile.length(), "application/octet-stream");
                minioService.uploadToMinio(metricsIn, metricsBucket, metricsKey, metricsFile.length(), "application/json");
            }

            log.info("Model uploaded to MinIO: {}", bucketResolver.resolve(BucketTypeEnum.MODEL));
            log.info("Metrics uploaded to MinIO: {}", bucketResolver.resolve(BucketTypeEnum.METRICS));

            String modelUrl = modelService.generateMinioUrl(modelBucket, modelKey);
            String metricsUrl = modelService.generateMinioUrl(metricsBucket, metricsKey);

            // 9. Persist Training record
            TrainingStatus completed = trainingStatusRepository.findByName(TrainingStatusEnum.COMPLETED)
                    .orElseThrow(() -> new IllegalStateException("TrainingStatus COMPLETED not found"));


            //TODO WE HAVE TO IMPLEMENT THE LOGIC OF ALGORITHM_CONFIGURATION
            //TODO WE HAVE TO CREATE TRAINING AT THE START OF THIS METHOD
             training = Training.builder()
                    .customAlgorithmConfiguration(savedConfig)
                    .user(user)
                    .datasetConfiguration(datasetConfig)
                    .status(completed)
                    .finishedDate(ZonedDateTime.now())
                    .results(Files.readString(metricsFile.toPath()))
                    .build();

            trainingRepository.save(training);


            // 10. Save model entity
            ModelType customType = modelTypeRepository.findByName(ModelTypeEnum.CUSTOM)
                    .orElseThrow(() -> new IllegalStateException("AlgorithmType CUSTOM not found"));

            modelService.saveModel(training, modelUrl, metricsUrl, customType, user);

            Model model = modelRepository.findByTraining(training)
                    .orElseThrow(() -> new EntityNotFoundException("Model not linked to training"));

            training.setModel(model);
            trainingRepository.save(training);

            // 11. Complete async task
            taskStatusService.completeTask(taskId);
            task.setModelId(model.getId());
            task.setTrainingId(model.getTraining().getId());
            taskStatusRepository.save(task);

            log.info("✅ Training complete [taskId={}] with modelId={}", taskId, model.getId());

        } catch (Exception e) {
            log.error("❌ Training failed [taskId={}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, e.getMessage());
            throw new RuntimeException("Training failed", e);
        } finally {
            try {
                if (dataDir != null) FileUtils.deleteDirectory(dataDir.toFile());
                if (outputDir != null) FileUtils.deleteDirectory(outputDir.toFile());
            } catch (IOException cleanupEx) {
                log.warn("⚠️ Failed to clean up temp directories: {}", cleanupEx.getMessage());
            }
        }
    }
}





