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
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.DatasetFunctionalTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
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
    private final TrainingRepository trainingRepository;
    private final TrainingStatusRepository trainingStatusRepository;
    private final ModelRepository modelRepository;

    public String handleCustomTrainingRequest(@Valid CustomTrainRequest request, AccountDetails accountDetails) {
        User user = accountDetails.getUser();
        String taskId = UUID.randomUUID().toString();
        String username = accountDetails.getUsername();

        // Detect retrain mode
        boolean hasTrainingId = request.getTrainingId() != null;
        boolean hasModelId = request.getModelId() != null;
        boolean retrainMode = hasTrainingId || hasModelId;

        // Validation
        if (hasTrainingId && hasModelId) {
            throw new BadRequestException("Cannot retrain from both a training and a model. Choose one.");
        }

        // Find base training for retrain mode
        Training retrainedFrom = null;
        if (hasTrainingId) {
            retrainedFrom = trainingRepository.findById(request.getTrainingId())
                    .orElseThrow(() -> new EntityNotFoundException("Training not found: " + request.getTrainingId()));
            if (retrainedFrom.getCustomAlgorithmConfiguration() == null) {
                throw new BadRequestException("Training " + request.getTrainingId() + " is not a custom algorithm training.");
            }
            log.info("🔁 Re-training custom model from trainingId={}", retrainedFrom.getId());
        } else if (hasModelId) {
            Model model = modelRepository.findById(request.getModelId())
                    .orElseThrow(() -> new EntityNotFoundException("Model not found: " + request.getModelId()));
            if (!model.isFinalized()) {
                throw new BadRequestException("Model " + request.getModelId() + " is not finalized. Only finalized models can be retrained.");
            }
            retrainedFrom = trainingRepository.findByModel(model)
                    .orElseThrow(() -> new EntityNotFoundException("Training not found for model: " + request.getModelId()));
            if (retrainedFrom.getCustomAlgorithmConfiguration() == null) {
                throw new BadRequestException("Model " + request.getModelId() + " is not from a custom algorithm training.");
            }
            log.info("🔁 Re-training custom model from modelId={}, trainingId={}", model.getId(), retrainedFrom.getId());
        }

        // Resolve algorithm
        CustomAlgorithm algorithm;
        if (request.getAlgorithmId() != null) {
            algorithm = customAlgorithmRepository.findWithOwnerById(request.getAlgorithmId())
                    .orElseThrow(() -> new IllegalArgumentException("Algorithm not found: " + request.getAlgorithmId()));
        } else if (retrainMode) {
            algorithm = retrainedFrom.getCustomAlgorithmConfiguration().getAlgorithm();
            log.info("📋 Using algorithm from base training: {}", algorithm.getName());
        } else {
            throw new BadRequestException("algorithmId is required for new training.");
        }

        // Check algorithm access
        if (!algorithm.getAccessibility().getName().equals(AlgorithmAccessibiltyEnum.PUBLIC)
                && !algorithm.getOwner().getUsername().equals(user.getUsername())) {
            throw new AccessDeniedException("User not authorized to train this algorithm");
        }

        taskStatusService.initTask(taskId, TaskTypeEnum.TRAINING, username);

        // Resolve dataset
        Dataset dataset;
        boolean hasFile = request.getDatasetFile() != null && !request.getDatasetFile().isEmpty();

        if (hasFile) {
            dataset = datasetService.uploadDataset(
                    request.getDatasetFile(), user, DatasetFunctionalTypeEnum.TRAIN
            ).getDataHeader();
            log.info("📂 New dataset uploaded: {}", dataset.getFileName());
        } else if (retrainMode) {
            DatasetConfiguration baseDatasetConfig = retrainedFrom.getDatasetConfiguration();
            if (baseDatasetConfig == null || baseDatasetConfig.getDataset() == null) {
                throw new BadRequestException("Base training has no dataset configuration.");
            }
            dataset = baseDatasetConfig.getDataset();
            log.info("📋 Using dataset from base training: {}", dataset.getFileName());
        } else {
            throw new BadRequestException("datasetFile is required for new training.");
        }

        // Resolve dataset configuration
        DatasetConfiguration datasetConfig = new DatasetConfiguration();
        datasetConfig.setDataset(dataset);
        datasetConfig.setUploadDate(ZonedDateTime.now());

        if (request.getBasicAttributesColumns() != null) {
            datasetConfig.setBasicAttributesColumns(request.getBasicAttributesColumns());
        } else if (retrainMode && retrainedFrom.getDatasetConfiguration() != null) {
            datasetConfig.setBasicAttributesColumns(retrainedFrom.getDatasetConfiguration().getBasicAttributesColumns());
        }

        if (request.getTargetColumn() != null) {
            datasetConfig.setTargetColumn(request.getTargetColumn());
        } else if (retrainMode && retrainedFrom.getDatasetConfiguration() != null) {
            datasetConfig.setTargetColumn(retrainedFrom.getDatasetConfiguration().getTargetColumn());
        }

        datasetConfig = datasetConfigurationRepository.save(datasetConfig);

        // Create Training entity with retrain linkage
        // Note: CustomAlgorithmConfiguration will be created by CustomTrainingService
        Training training = new Training();
        training.setUser(user);
        training.setDatasetConfiguration(datasetConfig);
        training.setRetrainedFrom(retrainedFrom);
        training.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.REQUESTED)
                .orElseThrow(() -> new EntityNotFoundException("TrainingStatus REQUESTED not found")));
        training = trainingRepository.save(training);

        log.info("✅ Custom training created: trainingId={}, algorithmId={}, retrainedFrom={}",
                training.getId(), algorithm.getId(), retrainedFrom != null ? retrainedFrom.getId() : "none");

        // Handle parameters file
        String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
        String paramsKey = null;
        String paramsBucket = null;

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
            log.info("📭 No params.json provided. Will use default algorithm parameters.");
        }

        CustomTrainMetadata metadata = new CustomTrainMetadata(
                dataset.getFileName(),
                bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET),
                datasetConfig.getId(),
                algorithm.getId(),
                paramsKey,
                paramsBucket,
                training.getId()  // Pass pre-created training ID
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
