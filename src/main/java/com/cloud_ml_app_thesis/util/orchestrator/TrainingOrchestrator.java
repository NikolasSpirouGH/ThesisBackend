package com.cloud_ml_app_thesis.util.orchestrator;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.train.CustomTrainRequest;
import com.cloud_ml_app_thesis.dto.request.train.TrainingStartRequest;
import com.cloud_ml_app_thesis.dto.train.DeferredCustomTrainInput;
import com.cloud_ml_app_thesis.dto.train.DeferredWekaTrainInput;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.service.TaskStatusService;
import com.cloud_ml_app_thesis.util.AsyncManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrainingOrchestrator {

    private final CustomAlgorithmRepository customAlgorithmRepository;
    private final AsyncManager asyncManager;
    private final TaskStatusService taskStatusService;
    private final TrainingRepository trainingRepository;
    private final ModelRepository modelRepository;
    private final DatasetRepository datasetRepository;

    /**
     * Handles custom algorithm training requests.
     * Returns taskId immediately after validation; MinIO upload and entity creation happen async.
     */
    public String handleCustomTrainingRequest(@Valid CustomTrainRequest request, AccountDetails accountDetails) {
        User user = accountDetails.getUser();
        String username = accountDetails.getUsername();

        // ========== VALIDATION (synchronous - throws immediately on error) ==========

        // Detect retrain mode
        boolean hasTrainingId = request.getTrainingId() != null;
        boolean hasModelId = request.getModelId() != null;
        boolean retrainMode = hasTrainingId || hasModelId;

        if (hasTrainingId && hasModelId) {
            throw new BadRequestException("Cannot retrain from both a training and a model. Choose one.");
        }

        // Find base training for retrain mode (for validation only)
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

        // Resolve and validate algorithm
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

        // Validate dataset requirement for non-retrain mode
        boolean hasFile = request.getDatasetFile() != null && !request.getDatasetFile().isEmpty();
        boolean hasDatasetId = request.getDatasetId() != null;

        if (hasFile && hasDatasetId) {
            throw new BadRequestException("Provide either datasetFile or datasetId, not both.");
        }
        if (!hasFile && !hasDatasetId && !retrainMode) {
            throw new BadRequestException("Either datasetFile or datasetId is required for new training.");
        }

        // Validate access if using existing dataset
        if (hasDatasetId) {
            Dataset ds = datasetRepository.findById(request.getDatasetId())
                    .orElseThrow(() -> new EntityNotFoundException("Dataset not found: " + request.getDatasetId()));
            boolean canAccess = ds.getUser().getUsername().equals(user.getUsername())
                    || ds.getAccessibility().getName().equals(DatasetAccessibilityEnum.PUBLIC);
            if (!canAccess) {
                throw new AccessDeniedException("User not authorized to use this dataset");
            }
        }

        // ========== SAVE TO TEMP + INIT TASK + ASYNC (non-blocking) ==========

        String taskId = taskStatusService.initTask(TaskTypeEnum.TRAINING, username);
        log.info("📋 Task initialized [taskId={}] for custom training", taskId);

        try {
            // Copy files to temp (fast local disk operation)
            Path tempDataset = copyToTemp(request.getDatasetFile());
            Path tempParams = copyToTemp(request.getParametersFile());

            // Build deferred input
            DeferredCustomTrainInput input = new DeferredCustomTrainInput(
                    tempDataset,
                    hasFile ? request.getDatasetFile().getOriginalFilename() : null,
                    hasFile ? request.getDatasetFile().getContentType() : null,
                    hasFile ? request.getDatasetFile().getSize() : 0,
                    tempParams,
                    tempParams != null ? request.getParametersFile().getOriginalFilename() : null,
                    request.getAlgorithmId(),
                    request.getTrainingId(),
                    request.getModelId(),
                    request.getBasicAttributesColumns(),
                    request.getTargetColumn(),
                    request.getDatasetId()  // existingDatasetId
            );

            // Fire and forget - async thread will handle MinIO upload and training
            asyncManager.setupAndTrainCustom(taskId, user.getId(), username, user, input);

            return taskId;

        } catch (IOException e) {
            log.error("❌ Failed to create temp files for task [{}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, "Failed to process uploaded files: " + e.getMessage());
            throw new RuntimeException("Failed to process uploaded files", e);
        }
    }

    /**
     * Handles Weka (predefined algorithm) training requests.
     * Returns taskId immediately after validation; MinIO upload and entity creation happen async.
     */
    public String handleTrainingRequest(@Valid TrainingStartRequest request, User user) {
        String username = user.getUsername();

        // ========== INIT TASK + SAVE TO TEMP + ASYNC (non-blocking) ==========

        String taskId = taskStatusService.initTask(TaskTypeEnum.TRAINING, username);
        log.info("📋 Task initialized [taskId={}] for Weka training", taskId);

        try {
            // Copy file to temp (fast local disk operation)
            Path tempDataset = copyToTemp(request.getFile());

            // Build deferred input from request fields
            DeferredWekaTrainInput input = new DeferredWekaTrainInput(
                    tempDataset,
                    tempDataset != null ? request.getFile().getOriginalFilename() : null,
                    tempDataset != null ? request.getFile().getContentType() : null,
                    tempDataset != null ? request.getFile().getSize() : 0,
                    request.getAlgorithmId(),
                    request.getAlgorithmConfigurationId(),
                    request.getDatasetId(),
                    request.getDatasetConfigurationId(),
                    request.getBasicCharacteristicsColumns(),
                    request.getTargetClassColumn(),
                    request.getOptions(),
                    request.getTrainingId(),
                    request.getModelId()
            );

            // Fire and forget - async thread will handle MinIO upload, entity creation, and training
            asyncManager.setupAndTrainWeka(taskId, user.getId(), username, user, input);

            return taskId;

        } catch (IOException e) {
            log.error("❌ Failed to create temp files for task [{}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, "Failed to process uploaded files: " + e.getMessage());
            throw new RuntimeException("Failed to process uploaded files", e);
        }
    }

    /**
     * Copies a MultipartFile to a temp file on local disk.
     * This is a fast operation compared to MinIO upload.
     */
    private Path copyToTemp(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        Path temp = Files.createTempFile("upload-", "-" + file.getOriginalFilename());
        file.transferTo(temp.toFile());
        log.debug("📁 Copied {} to temp: {}", file.getOriginalFilename(), temp);
        return temp;
    }
}
