package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.train.CustomTrainMetadata;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.exception.AlgorithmNotFoundException;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.accessibility.DatasetAccessibilityRepository;
import com.cloud_ml_app_thesis.repository.AlgorithmTypeRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.util.DockerContainerRunner;
import com.cloud_ml_app_thesis.util.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomAlgorithmTrainingService {

    private final CustomAlgorithmRepository customAlgorithmRepository;
    private final BucketResolver bucketResolver;
    private final MinioService minioService;
    private final TrainingRepository trainingRepository;
    private final ModelService modelService;
    private final DockerContainerRunner dockerCommandRunner;
    private final TrainingStatusRepository trainingStatusRepository;
    private final AlgorithmImageRepository imageRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final AlgorithmTypeRepository algorithmTypeRepository;
    private final DatasetAccessibilityRepository datasetAccessibilityRepository;
    private final CategoryRepository categoryRepository;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final ModelRepository modelRepository;


    @Transactional
    public void train(String taskId, User user, CustomTrainMetadata metadata) {
        log.info("🧠 Starting training [taskId={}] for user={}", taskId, user.getUsername());

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Async task tracking not found"));
        task.setStatus(TaskStatusEnum.RUNNING);
        taskStatusRepository.save(task);

        Path dataDir = null;
        Path outputDir = null;

        try {
            // 1. Validate access
            CustomAlgorithm algorithm = customAlgorithmRepository.findById(metadata.algorithmId())
                    .orElseThrow(() -> new AlgorithmNotFoundException("Algorithm not found: id=" + metadata.algorithmId()));

            if (!algorithm.getAccessibility().getName().equals(AlgorithmAccessibiltyEnum.PUBLIC)
                    && !algorithm.getOwner().getUsername().equals(user.getUsername())) {
                task.setStatus(TaskStatusEnum.FAILED);
                task.setErrorMessage("Not authorized");
                taskStatusRepository.save(task);
                throw new AuthorizationDeniedException("User not authorized to train this algorithm");
            }
            // 2. Load dataset & configuration
            Path datasetPath = minioService.downloadObjectToTempFile(
                    metadata.datasetBucket(), metadata.datasetKey());
            log.info("📥 Dataset [{}] downloaded to: {}", metadata.datasetKey(), datasetPath);

            DatasetConfiguration config = datasetConfigurationRepository.findById(metadata.datasetConfigurationId())
                    .orElseThrow(() -> new IllegalStateException("DatasetConfiguration not found"));

            boolean alreadyTrained = trainingRepository
                    .existsByCustomAlgorithmIdAndDatasetConfigurationId(algorithm.getId(), config.getId());

            if (alreadyTrained) {
                log.warn("🚫 Training already exists for algorithm={} and datasetConfiguration={}",
                        algorithm.getId(), config.getId());
                task.setStatus(TaskStatusEnum.FAILED);
                task.setErrorMessage("Task already trained");
                taskStatusRepository.save(task);
                throw new IllegalStateException("This algorithm is already trained on the same dataset");
            }

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

                dockerCommandRunner.loadDockerImageFromTar(tarPath);

                dockerImageTag = "user-defined/custom:latest"; // δικό σου fixed tag
            } else if (StringUtils.isNotBlank(activeImage.getDockerHubUrl())) {
                dockerImageTag = activeImage.getDockerHubUrl();
                dockerCommandRunner.pullDockerImage(dockerImageTag);
            } else {
                task.setStatus(TaskStatusEnum.FAILED);
                task.setErrorMessage("No image source found");
                taskStatusRepository.save(task);
                throw new IllegalStateException("Active image has neither dockerTarKey nor dockerHubUrl");
            }

            // 5. Prepare /data & /model directories
            Path basePath = FileUtil.getSharedPathRoot();
            dataDir = Files.createTempDirectory(basePath, "training-ds-");
            outputDir = Files.createTempDirectory(basePath, "training-out-");

            FileUtil.writeParamsJson(algorithm, config, dataDir);

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

            // 8. Upload results to MinIO
            String modelKey = user.getUsername() + "-" + modelFile.getName();
            String metricsKey = user.getUsername() + "-" + metricsFile.getName();
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

            Training training = Training.builder()
                    .customAlgorithm(algorithm)
                    .user(user)
                    .datasetConfiguration(config)
                    .status(completed)
                    .startedDate(ZonedDateTime.now())
                    .finishedDate(ZonedDateTime.now())
                    .results(Files.readString(metricsFile.toPath()))
                    .build();

            trainingRepository.save(training);

            // 10. Save model entity
            AlgorithmType customType = algorithmTypeRepository.findByName(AlgorithmTypeEnum.CUSTOM)
                    .orElseThrow(() -> new IllegalStateException("AlgorithmType CUSTOM not found"));

            modelService.saveModel(training, modelUrl, metricsUrl, customType, user);

            Model model = modelRepository.findByTraining(training)
                    .orElseThrow(() -> new EntityNotFoundException("Model not linked to training"));

            training.setModel(model);
            trainingRepository.save(training);

            // 11. Complete async task
            task.setStatus(TaskStatusEnum.COMPLETED);
            task.setTaskType(TaskTypeEnum.TRAINING);
            task.setFinishedAt(ZonedDateTime.now());
            task.setResultUrl(modelUrl);
            taskStatusRepository.save(task);

            log.info("✅ Training complete [taskId={}] with modelId={}", taskId, model.getId());

        } catch (Exception e) {
            log.error("❌ Training failed [taskId={}]: {}", taskId, e.getMessage(), e);
            task.setStatus(TaskStatusEnum.FAILED);
            task.setFinishedAt(ZonedDateTime.now());
            taskStatusRepository.save(task);
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





