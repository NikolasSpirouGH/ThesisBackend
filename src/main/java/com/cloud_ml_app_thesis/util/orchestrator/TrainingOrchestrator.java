package com.cloud_ml_app_thesis.util.orchestrator;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.train.CustomTrainRequest;
import com.cloud_ml_app_thesis.dto.request.train.TrainingStartRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.train.CustomTrainMetadata;
import com.cloud_ml_app_thesis.dto.train.PredefinedTrainMetadata;
import com.cloud_ml_app_thesis.dto.train.WekaContainerTrainMetadata;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.DatasetFunctionalTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.service.DatasetService;
import com.cloud_ml_app_thesis.service.MinioService;
import com.cloud_ml_app_thesis.service.TaskStatusService;
import com.cloud_ml_app_thesis.util.AsyncManager;
import com.cloud_ml_app_thesis.util.TrainingHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrainingOrchestrator {

    private final DatasetService datasetService;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final BucketResolver bucketResolver;
    private final CustomAlgorithmRepository customAlgorithmRepository;
    private final AsyncManager asyncManager;
    private final MinioService minioService;
    private final TrainingHelper trainingHelper;
    private final TaskStatusService taskStatusService;

    public String handleCustomTrainingRequest(@Valid CustomTrainRequest request, AccountDetails accountDetails) {
        User user = accountDetails.getUser();
        CustomAlgorithm algorithm = customAlgorithmRepository.findWithOwnerById(request.getAlgorithmId())
                .orElseThrow(() -> new IllegalArgumentException("Algorithm not found"));

        if (!algorithm.getAccessibility().getName().equals(AlgorithmAccessibiltyEnum.PUBLIC)
                && !algorithm.getOwner().getUsername().equals(user.getUsername())) {
            throw new AccessDeniedException("User not authorized to train this algorithm");
        }
        String taskId = UUID.randomUUID().toString();
        String username = accountDetails.getUsername();

        taskStatusService.initTask(taskId, TaskTypeEnum.TRAINING, username);

        //TODO Πρεπει να χρησημοποιησουμε trainHelper

        Dataset dataset = datasetService.uploadDataset(
                request.getDatasetFile(), user, DatasetFunctionalTypeEnum.TRAIN
        ).getDataHeader();

        DatasetConfiguration config = new DatasetConfiguration();
        config.setDataset(dataset);
        config.setUploadDate(ZonedDateTime.now());
        config.setBasicAttributesColumns(request.getBasicAttributesColumns());
        config.setTargetColumn(request.getTargetColumn());
        config = datasetConfigurationRepository.save(config);

        String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
        String paramsKey = null;
        String paramsBucket = null;
        log.info("▶ parametersFile: {}", request.getParametersFile());
        log.info("▶ isEmpty: {}", request.getParametersFile() != null ? request.getParametersFile().isEmpty() : "null");

        if (request.getParametersFile() != null && !request.getParametersFile().isEmpty()) {
            paramsKey = user.getUsername() + "_" + timestamp + "_" + request.getParametersFile().getOriginalFilename();
            paramsBucket = bucketResolver.resolve(BucketTypeEnum.PARAMETERS);
            try {
                minioService.uploadObjectToBucket(request.getParametersFile(), paramsBucket, paramsKey);
            } catch (IOException e) {
                throw new FileProcessingException("Failed to upload parameters file to PARAMETERS bucket", e);
            }
            log.info("✅ Uploaded parameters file to MinIO: {}/{}", paramsBucket, paramsKey);
        } else {
            //TODO TEST IT
            log.info("📭 No params.json provided. Will use default algorithm parameters.");
        }

        CustomTrainMetadata metadata = new CustomTrainMetadata(
                dataset.getFileName(),
                bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET),
                config.getId(),
                request.getAlgorithmId(),
                paramsKey,
                paramsBucket
        );
        asyncManager.customTrainAsync(taskId, user.getId(), username, metadata);

        return taskId;
    }

    public String handleTrainingRequest(@Valid TrainingStartRequest request, User user) {
        String taskId = UUID.randomUUID().toString();
        String username = user.getUsername();

        try {
            // Init async task
            taskStatusService.initTask(taskId, TaskTypeEnum.TRAINING, username);

            // Prepare training input (this creates Training, DatasetConfiguration, AlgorithmConfiguration)
            TrainingDataInput input = trainingHelper.configureTrainingDataInputByTrainCase(request, user);

            if (input.getErrorResponse() != null) {
                log.warn("⚠️ Invalid training input [taskId={}, reason={}]", taskId, input.getErrorResponse().getMessage());
                throw new BadRequestException(input.getErrorResponse().getMessage());
            }

            // Use containerized Weka training (Kubernetes job)
            // Get dataset info from the configuration
            DatasetConfiguration datasetConfig = input.getDatasetConfiguration();
            String datasetBucket = bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET);
            String datasetKey = datasetConfig.getDataset().getFileName();

            WekaContainerTrainMetadata containerMetadata = new WekaContainerTrainMetadata(
                    input.getTraining().getId(),
                    datasetConfig.getId(),
                    input.getAlgorithmConfiguration().getId(),
                    datasetBucket,
                    datasetKey,
                    datasetConfig.getTargetColumn(),
                    datasetConfig.getBasicAttributesColumns()
            );

            log.info("🚀 Submitting Weka container training [taskId={}] for user={}", taskId, username);
            asyncManager.trainWekaContainerAsync(taskId, user.getId(), username, containerMetadata);

            return taskId;

        } catch (Exception e) {
            log.error("❌ Failed to handle training [taskId={}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, e.getMessage());
            throw new RuntimeException("Failed to handle training [taskId=" + taskId + "]", e);
        }
    }
}
