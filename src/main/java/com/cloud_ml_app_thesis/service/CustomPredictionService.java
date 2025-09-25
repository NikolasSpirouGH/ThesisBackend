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
import com.cloud_ml_app_thesis.util.DockerContainerRunner;
import com.cloud_ml_app_thesis.util.FileUtil;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomPredictionService {

    private final TaskStatusRepository taskStatusRepository;
    private final ModelRepository modelRepository;
    private final BucketResolver bucketResolver;
    private final MinioService minioService;
    private final ModelExecutionStatusRepository modelExecutionStatusRepository;
    private final ModelExecutionRepository modelExecutionRepository;
    private final ModelService modelService;
    private final DockerContainerRunner dockerContainerRunner;
    private final TaskStatusService taskStatusService;


    @Transactional
    public void executeCustom(String taskId, Integer modelId, String datasetKey, User user) {
        log.info("🎯 [SYNC] Starting prediction [taskId={}] for modelId={} by user={}", taskId, modelId, user.getUsername());

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Missing task record"));

        ModelExecution execution = null;
        Path dataDir = null;
        Path outputDir = null;

        try {
            task.setStatus(TaskStatusEnum.RUNNING);
            taskStatusRepository.save(task);

            // 1. Ανάκτηση μοντέλου
            Model model = modelRepository.findById(modelId)
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

            Files.createDirectories(dataDir);
            Files.createDirectories(outputDir);

            // 3. Κατέβασμα dataset και αντιγραφή σε /data
            Path datasetPath = minioService.downloadObjectToTempFile(inputBucket, datasetKey);
            Path datasetInside = dataDir.resolve("predict.csv");
            Files.copy(datasetPath, datasetInside, StandardCopyOption.REPLACE_EXISTING);
            log.info("📥 Prediction dataset copied to /data: {}", datasetInside);

            // 4. Κατέβασμα μοντέλου και αντιγραφή σε /model
            String modelKey = minioService.extractMinioKey(model.getModelUrl());
            Path modelPath = minioService.downloadObjectToTempFile(modelBucket, modelKey);
            Path modelInside = outputDir.resolve("trained_model.pkl");
            Files.copy(modelPath, modelInside, StandardCopyOption.REPLACE_EXISTING);
            log.info("📥 Trained model copied to /model: {}", modelInside);

            // Copy standardized predict.py template to data directory
            try (InputStream predictTemplate = getClass().getResourceAsStream("/templates/predict.py")) {
                if (predictTemplate == null) {
                    throw new IllegalStateException("❌ predict.py template not found in resources");
                }
                Path predictPyPath = dataDir.resolve("predict.py");
                Files.copy(predictTemplate, predictPyPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("✅ Copied standardized predict.py template to {}", predictPyPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy predict.py template", e);
            }

            // Rename prediction dataset to expected name
            Path testDataPath = dataDir.resolve("test_data.csv");
            Files.move(datasetInside, testDataPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("📥 Renamed prediction dataset to test_data.csv");

            // 5. Extract algorithm.py from Docker image for prediction
            CustomAlgorithm algorithm = model.getTraining().getCustomAlgorithmConfiguration().getAlgorithm();
            CustomAlgorithmImage activeImage = algorithm.getImages().stream()
                    .filter(CustomAlgorithmImage::isActive)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No active image found"));

            // Extract algorithm.py from the user's Docker image to /data directory
            try {
                dockerContainerRunner.copyFileFromImage(activeImage.getName(), "/app/algorithm.py", dataDir.resolve("algorithm.py"));
                log.info("✅ Extracted algorithm.py from Docker image for prediction");
            } catch (Exception e) {
                log.error("❌ Failed to extract algorithm.py for prediction: {}", e.getMessage());
                throw new RuntimeException("Could not extract algorithm.py for prediction", e);
            }

            dockerContainerRunner.runPredictionContainer(activeImage.getName(), dataDir, outputDir);

            // 6. Βρες output αρχείο
            File predictedFile = Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".csv") || p.toString().endsWith(".arff"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No valid .csv or .arff output found", null));

            // 7. Save ModelExecution
            execution = new ModelExecution();
            execution.setModel(model);
            execution.setExecutedByUser(user);
            execution.setExecutedAt(ZonedDateTime.now());
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.IN_PROGRESS)
                    .orElseThrow(() -> new EntityNotFoundException("Execution status not found")));
            modelExecutionRepository.save(execution);

            // 8. Upload σε MinIO
            String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
            String predKey = user.getUsername() + "_" + timestamp + "_" + predictedFile.getName();
            String predBucket = bucketResolver.resolve(BucketTypeEnum.PREDICTION_RESULTS);

            try (InputStream in = new FileInputStream(predictedFile)) {
                minioService.uploadToMinio(in, predBucket, predKey, predictedFile.length(), "text/csv");
            }

            String resultUrl = modelService.generateMinioUrl(predBucket, predKey);
            execution.setPredictionResult(resultUrl);
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FINISHED)
                    .orElseThrow(() -> new EntityNotFoundException("Execution status not found")));
            modelExecutionRepository.save(execution);

            // 9. Ολοκλήρωση task
            task.setExecutionId(execution.getId());
            taskStatusService.completeTask(taskId);
            taskStatusRepository.save(task);

            log.info("✅ Prediction complete [taskId={}]. Output at: {}", taskId, resultUrl);

        } catch (Exception e) {
            log.error("❌ Prediction failed [taskId={}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, e.getMessage());
            if (execution != null) {
                execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FAILED).orElse(null));
                modelExecutionRepository.save(execution);
            }
            taskStatusRepository.save(task);
            throw new RuntimeException("Prediction failed: " + e.getMessage(), e);
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


