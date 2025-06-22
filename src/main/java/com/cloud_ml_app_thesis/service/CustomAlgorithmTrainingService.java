package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.train.CustomTrainMetadata;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
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
import com.cloud_ml_app_thesis.util.DockerCommandRunner;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomAlgorithmTrainingService {

    private final CustomAlgorithmRepository customAlgorithmRepository;
    private final BucketResolver bucketResolver;
    private final MinioService minioService;
    private final TrainingRepository trainingRepository;
    private final ModelService modelService;
    private final DockerCommandRunner dockerCommandRunner;
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
        log.info("🧠 [SYNC] Starting training [taskId={}] for user={}", taskId, user.getUsername());

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Async task tracking not found"));
        task.setStatus(TaskStatusEnum.RUNNING);
        taskStatusRepository.save(task);

        try {
            // 1. Εύρεση custom αλγορίθμου
            CustomAlgorithm algorithm = customAlgorithmRepository.findById(metadata.algorithmId())
                    .orElseThrow(() -> new AlgorithmNotFoundException("Algorithm not found: id=" + metadata.algorithmId()));

            if (!Boolean.TRUE.equals(algorithm.getIsPublic()) && !algorithm.getOwner().equals(user.getUsername())) {
                throw new AuthorizationDeniedException("User not authorized to train this algorithm");
            }

            // 2. Λήψη dataset και configuration
            Path datasetPath = minioService.downloadObjectToTempFile(
                    metadata.datasetBucket(), metadata.datasetKey());
            log.info("📥 Dataset [{}] has been downloaded", datasetPath);

            DatasetConfiguration config = datasetConfigurationRepository.findById(metadata.datasetConfigurationId())
                    .orElseThrow(() -> new IllegalStateException("DatasetConfiguration not found"));

            // 3. Απενεργοποίηση προηγούμενων ενεργών εικόνων
            imageRepository.findByCustomAlgorithmAndIsActiveTrue(algorithm).forEach(img -> img.setActive(false));
            imageRepository.saveAll(imageRepository.findByCustomAlgorithmAndIsActiveTrue(algorithm));

            // 4. Εύρεση/φόρτωση docker image
            String dockerImage;
            CustomAlgorithmImage image = new CustomAlgorithmImage();
            image.setCustomAlgorithm(algorithm);
            image.setUploadedAt(ZonedDateTime.now());
            image.setActive(true);

            if (metadata.tarKey() != null) {
                Path tarPath = minioService.downloadObjectToTempFile(
                        bucketResolver.resolve(BucketTypeEnum.CUSTOM_ALGORITHM), metadata.tarKey());
                log.info("📦 TAR image downloaded: {}", tarPath);
                dockerCommandRunner.runCommand("docker", "load", "-i", tarPath.toString());
                dockerImage = "user-defined/custom:latest";
                image.setDockerTarKey(metadata.tarKey());
            } else {
                dockerImage = metadata.dockerHubUrl();
                dockerCommandRunner.runCommand("docker", "pull", dockerImage);
                image.setDockerHubUrl(dockerImage);
            }
            imageRepository.save(image);

            // 5. Αντιγραφή dataset σε προσωρινό φάκελο mount
            Path dataDir = Files.createTempDirectory(new File("/tmp").toPath(), "training-ds-dir-");

            Path datasetInside = dataDir.resolve("dataset.csv");
            Files.copy(datasetPath, datasetInside, StandardCopyOption.REPLACE_EXISTING);

            Path outputDir = Files.createTempDirectory(new File("/tmp").toPath(), "training-output-");

            log.info("📁 Training paths: /data={}, /model={}", dataDir, outputDir);
            dockerCommandRunner.runCommand("docker", "run",
                    "-v", dataDir + ":/data",
                    "-v", outputDir + ":/model",
                    dockerImage);

            // 6. Εύρεση αποτελεσμάτων & metrics
            File modelFile = Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No model file generated", null));

            File metricsFile = Files.walk(outputDir)
                    .filter(p -> p.getFileName().toString().equals("metrics.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No metrics file generated", null));

            // 7. Upload σε MinIO
            String modelKey = UUID.randomUUID() + "-" + modelFile.getName();
            String metricsKey = UUID.randomUUID() + "-" + metricsFile.getName();
            String modelBucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
            String metricsBucket = bucketResolver.resolve(BucketTypeEnum.METRICS);

            try (InputStream in = new FileInputStream(modelFile);
                 InputStream metricsIn = new FileInputStream(metricsFile)) {
                minioService.uploadToMinio(in, modelBucket, modelKey, modelFile.length(), "application/octet-stream");
                minioService.uploadToMinio(metricsIn, metricsBucket, metricsKey, metricsFile.length(), "application/json");
            }

            String modelUrl = modelService.generateMinioUrl(modelBucket, modelKey);
            String metricsUrl = modelService.generateMinioUrl(metricsBucket, metricsKey);

            // 8. Καταγραφή Training
            TrainingStatus completed = trainingStatusRepository.findByName(TrainingStatusEnum.COMPLETED)
                    .orElseThrow(() -> new IllegalStateException("TrainingStatus COMPLETED not found"));

            Training training = Training.builder()
                    .customAlgorithm(algorithm)
                    .user(user)
                    .datasetConfiguration(config)
                    .status(completed)
                    .startedDate(ZonedDateTime.now())
                    .finishedDate(ZonedDateTime.now())
                    .build();

            try {
                training.setResults(Files.readString(metricsFile.toPath()));
            } catch (IOException e) {
                log.warn("⚠️ metrics.json could not be read: {}", e.getMessage());
                training.setResults("Training completed successfully (metrics.json unreadable)");
            }

            trainingRepository.save(training);

            // 9. Save Model Entity
            AlgorithmType customType = algorithmTypeRepository.findByName(AlgorithmTypeEnum.CUSTOM)
                    .orElseThrow(() -> new IllegalStateException("AlgorithmType CUSTOM not found"));

            modelService.saveModel(training, modelUrl, metricsUrl, customType, user);

            Model model = modelRepository.findByTraining(training)
                    .orElseThrow(() -> new EntityNotFoundException("Model not linked to training"));

            training.setModel(model);
            trainingRepository.save(training);

            // 10. Ολοκλήρωση Task
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
        }
    }
//    @Transactional
//    public void train(String taskId, User user, CustomTrainMetadata metadata) {
//        log.info("🧠 [SYNC] Starting training [taskId={}] for user={}", taskId, user.getUsername());
//
//        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
//                .orElseThrow(() -> new IllegalStateException("Async task tracking not found"));
//
//        try {
//            task.setStatus(TaskStatusEnum.RUNNING);
//            taskStatusRepository.save(task);
//
//            // 1. Load custom algorithm
//            CustomAlgorithm algorithm = customAlgorithmRepository.findById(metadata.algorithmId())
//                    .orElseThrow(() -> new AlgorithmNotFoundException("Algorithm not found: id=" + metadata.algorithmId()));
//
//            if (!Boolean.TRUE.equals(algorithm.getIsPublic()) && !algorithm.getOwner().equals(user.getUsername())) {
//                throw new AuthorizationDeniedException("User not authorized to train this algorithm");
//            }
//
//            // 2. Load dataset & config
//            Path datasetPath = minioService.downloadObjectToTempFile(
//                    metadata.datasetBucket(), metadata.datasetKey());
//
//            log.info("Dataset [{}] has been downloaded", datasetPath);
//
//            DatasetConfiguration datasetConfiguration = datasetConfigurationRepository
//                    .findById(metadata.datasetConfigurationId())
//                    .orElseThrow(() -> new IllegalStateException("DatasetConfiguration not found"));
//
//            Dataset dataset = datasetConfiguration.getDataset();
//
//            // 3. Deactivate previous images
//            List<CustomAlgorithmImage> previousImages = imageRepository.findByCustomAlgorithmAndIsActiveTrue(algorithm);
//            previousImages.forEach(i -> i.setActive(false));
//            imageRepository.saveAll(previousImages);
//
//            // 4. Prepare Docker image
//            String dockerImage;
//            CustomAlgorithmImage algorithmImage = new CustomAlgorithmImage();
//            algorithmImage.setCustomAlgorithm(algorithm);
//            algorithmImage.setUploadedAt(ZonedDateTime.now());
//            algorithmImage.setActive(true);
//
//            if (metadata.tarKey() != null) {
//                log.info("Tar Key [{}]", metadata.tarKey());
//                Path tarPath = minioService.downloadObjectToTempFile(
//                        bucketResolver.resolve(BucketTypeEnum.CUSTOM_ALGORITHM), metadata.tarKey());
//                log.info("Tar Key [{}] has been downloaded", tarPath);
//                dockerCommandRunner.runCommand("docker", "load", "-i", tarPath.toString());
//                dockerImage = "user-defined/custom:latest";
//                algorithmImage.setDockerTarKey(metadata.tarKey());
//            } else {
//                dockerImage = metadata.dockerHubUrl();
//                algorithmImage.setDockerHubUrl(dockerImage);
//                dockerCommandRunner.runCommand("docker", "pull", dockerImage);
//            }
//
//            imageRepository.save(algorithmImage);
//
//            Path dataDir = Files.createTempDirectory("training-ds-dir-");
//            Path datasetInside = dataDir.resolve("dataset.csv");
//            Files.copy(datasetPath, datasetInside, StandardCopyOption.REPLACE_EXISTING);
//
//            if (!Files.isRegularFile(datasetInside)) {
//                throw new IllegalStateException("❌ dataset.csv not found in temp data dir: " + datasetInside);
//            }
//
//            log.info("✅ Copying dataset to {}", datasetInside.toAbsolutePath());
//            log.info("✅ Mounting data dir {}", dataDir.toAbsolutePath());
//
//            // 5. Prepare Docker mount paths
//            Path outputDir = Files.createTempDirectory("training-output-");
//
//            log.info("🚀 Executing docker run with mount:\n - Data dir = {}\n - Output   = {}", dataDir.toAbsolutePath(), outputDir.toAbsolutePath());
//
//            log.info("📁 DEBUG /data content before run:");
//            Files.list(dataDir).forEach(p -> log.info("📁 - {}", p));
//
//            dockerCommandRunner.runCommand("docker", "run",
//                    "-v", dataDir.toAbsolutePath() + ":/data",
//                    "-v", outputDir.toAbsolutePath() + ":/model",
//                    dockerImage
//            );
//
//            log.info("📁 Listing output directory contents for debug:");
//            Files.list(outputDir).forEach(p -> log.info("📁 Output file: {}", p.getFileName().toString()));
//
//            // 6. Save model
//            File modelFile = Files.walk(outputDir)
//                    .filter(Files::isRegularFile)
//                    .map(Path::toFile)
//                    .findFirst()
//                    .orElseThrow(() -> new FileProcessingException("No model file generated", null));
//
//            // Locate metrics file
//            File metricsFile = Files.walk(outputDir)
//                    .filter(p -> p.getFileName().toString().equals("metrics.json"))
//                    .map(Path::toFile)
//                    .findFirst()
//                    .orElseThrow(() -> new FileProcessingException("No metrics file generated", null));
//
//            String modelBucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
//            String metricsBucket = bucketResolver.resolve(BucketTypeEnum.METRICS);
//
//            String modelKey = UUID.randomUUID() + "-" + modelFile.getName();
//            String metricsKey = UUID.randomUUID() + "-" + metricsFile.getName();
//
//            try (InputStream in = new FileInputStream(modelFile);
//                 InputStream metricsIn = new FileInputStream(metricsFile)) {
//
//                // 🔄 Upload model
//                minioService.uploadToMinio(in, modelBucket, modelKey, modelFile.length(), "application/octet-stream");
//
//                // 📊 Upload metrics
//                minioService.uploadToMinio(metricsIn, metricsBucket, metricsKey, metricsFile.length(), "application/json");
//            }
//            String modelUrl = modelService.generateMinioUrl(modelBucket, modelKey);
//            String metricsUrl = modelService.generateMinioUrl(metricsBucket, metricsKey);
//
//            // 7. Save training
//            TrainingStatus completed = trainingStatusRepository.findByName(TrainingStatusEnum.COMPLETED)
//                    .orElseThrow(() -> new IllegalStateException("TrainingStatus COMPLETED not found"));
//
//            Training training = Training.builder()
//                    .customAlgorithm(algorithm)
//                    .user(user)
//                    .datasetConfiguration(datasetConfiguration)
//                    .status(completed)
//                    .startedDate(ZonedDateTime.now())
//                    .finishedDate(ZonedDateTime.now())
//                    .build();
//
//            String results;
//
//            if (metricsFile.exists()) {
//                try {
//                    String json = Files.readString(metricsFile.toPath());
//                    results = json; // ή κάνε deserialize σε DTO αν θες
//                    log.info("📊 Training metrics loaded: {}", json);
//                } catch (IOException e) {
//                    log.warn("⚠️ Could not read metrics.json, using fallback message");
//                    results = "Training completed successfully (metrics.json could not be read)";
//                }
//            } else {
//                results = "Training completed successfully (no metrics.json found)";
//            }
//            training.setResults(results);
//            trainingRepository.save(training);
//
//            // 8. Save model entity
//            AlgorithmType customType = algorithmTypeRepository.findByName(AlgorithmTypeEnum.CUSTOM).orElseThrow(() -> new IllegalStateException("Custom Algorithm type not found"));
//            modelService.saveModel(training, modelUrl, metricsUrl , customType, user);
//
//            Model model = modelRepository.findByTraining(training)
//                    .orElseThrow(() -> new EntityNotFoundException("Model not linked to training"));
//            training.setModel(model);
//            trainingRepository.save(training);
//
//            // 9. Update task
//            task.setStatus(TaskStatusEnum.COMPLETED);
//            task.setTaskType(TaskTypeEnum.TRAINING);
//            task.setFinishedAt(ZonedDateTime.now());
//            task.setResultUrl(modelUrl);
//            taskStatusRepository.save(task);
//
//            log.info("Model after training with id: {}", model.getId());
//            log.info("Training completed successfully with id: {}", training.getId());
//            log.info("✅ Training complete [taskId={}]  Model uploaded to: {}", taskId, modelUrl);
//
//        } catch (Exception e) {
//            log.error("❌ Training failed [taskId={}]: {}", taskId, e.getMessage(), e);
//            task.setStatus(TaskStatusEnum.FAILED);
//            task.setFinishedAt(ZonedDateTime.now());
//            taskStatusRepository.save(task);
//            throw new RuntimeException("Training failed", e);
//        }
//    }

}





