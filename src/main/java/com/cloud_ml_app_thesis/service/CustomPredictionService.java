package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.entity.CustomAlgorithm;
import com.cloud_ml_app_thesis.entity.CustomAlgorithmImage;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.model.ModelExecution;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.ModelExecutionStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.model.ModelExecutionRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.ModelExecutionStatusRepository;
import com.cloud_ml_app_thesis.util.DockerContainerRunner;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
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


    @Transactional
    public void executeCustom(String taskId, Integer modelId, String datasetKey, User user) {
        log.info("🎯 [SYNC] Starting prediction [taskId={}] for modelId={} by user={}", taskId, modelId, user.getUsername());

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Missing task record"));

        ModelExecution execution = null;
        String resultUrl = null;

        try {
            task.setStatus(TaskStatusEnum.RUNNING);
            taskStatusRepository.save(task);

            // 1. Ανάκτηση μοντέλου
            Model model = modelRepository.findById(modelId)
                    .orElseThrow(() -> new EntityNotFoundException("Model with ID " + modelId + " not found"));

            if (!model.getAlgorithmType().getName().equals(AlgorithmTypeEnum.CUSTOM)) {
                task.setStatus(TaskStatusEnum.FAILED);
                task.setErrorMessage("Not a custom algorithm");
                task.setFinishedAt(ZonedDateTime.now());
                throw new IllegalArgumentException("This model is not Docker-based");
            }

            String inputBucket = bucketResolver.resolve(BucketTypeEnum.PREDICT_DATASET);

            // ⬇️ 2. Download input dataset from MinIO to temp
            Path predictionDataset = minioService.downloadObjectToTempFile(inputBucket, datasetKey);

            // 3. Κατέβασμα μοντέλου από MinIO
            String modelBucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
            String modelKey = minioService.extractMinioKey(model.getModelUrl());
            Path inputData = minioService.downloadObjectToTempFile(modelBucket, modelKey);

            log.info("🧠 model.getModelUrl() = {}", model.getModelUrl());

            // 4. Δημιουργία output dir
            Path outputDir = Files.createTempDirectory("predict-out-");

            CustomAlgorithm algorithm = model.getTraining().getCustomAlgorithmConfiguration().getAlgorithm();

            CustomAlgorithmImage activeImage = algorithm.getImages().stream()
                    .filter(CustomAlgorithmImage::isActive)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No active image found"));

            // 5. Εκτέλεση docker container
            dockerContainerRunner.runPredictionContainer(activeImage.getName(), predictionDataset, inputData, outputDir);

            // 6. Βρες output αρχείο
            File[] predictionFiles = outputDir.toFile().listFiles();

            if (predictionFiles == null || predictionFiles.length == 0) {
                task.setStatus(TaskStatusEnum.FAILED);
                task.setErrorMessage("No prediction files found");
                task.setFinishedAt(ZonedDateTime.now());
                throw new FileProcessingException("No prediction output found", null);
            }

            File predictedFile = Arrays.stream(predictionFiles)
                    .filter(f -> f.getName().endsWith(".csv") || f.getName().endsWith(".arff"))
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No valid .csv or .arff output found", null));

            // 7. Save ModelExecution
            execution = new ModelExecution();
            execution.setModel(model);
            execution.setExecutedByUser(user);
            execution.setExecutedAt(LocalDateTime.now());
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.IN_PROGRESS)
                    .orElseThrow(() -> new EntityNotFoundException("Execution status not found")));

            modelExecutionRepository.save(execution);

            // 8. Upload output σε MinIO
            String predKey = UUID.randomUUID() + "-" + predictedFile.getName();
            String predBucket = bucketResolver.resolve(BucketTypeEnum.PREDICTION_RESULTS);

            try (InputStream in = new FileInputStream(predictedFile)) {
                minioService.uploadToMinio(in, predBucket, predKey, predictedFile.length(), "text/csv");
            }

            resultUrl = modelService.generateMinioUrl(predBucket, predKey);

            execution.setPredictionResult(resultUrl);
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FINISHED)
                    .orElseThrow(() -> new EntityNotFoundException("Execution status not found")));
            modelExecutionRepository.save(execution);

            // 9. Ενημέρωση task
            task.setStatus(TaskStatusEnum.COMPLETED);
            task.setFinishedAt(ZonedDateTime.now());
            task.setResultUrl(resultUrl);
            log.info("Execution id inside model execution service: {}", execution.getId());
            task.setExecutionId(execution.getId());
            taskStatusRepository.save(task);

            log.info("✅ Prediction complete [taskId={}]. Output at: {}", taskId, resultUrl);

        } catch (Exception e) {
            log.error("❌ Prediction failed [taskId={}]: {}", taskId, e.getMessage(), e);
            task.setStatus(TaskStatusEnum.FAILED);
            task.setFinishedAt(ZonedDateTime.now());
            if (execution != null) {
                execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FAILED)
                        .orElse(null));
                modelExecutionRepository.save(execution);
            }
            taskStatusRepository.save(task);
            throw new RuntimeException("Prediction failed: " + e.getMessage(), e);
        }
    }
}
