package com.cloud_ml_app_thesis.util.orchestrator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_ml_app_thesis.dto.request.execution.ExecuteRequest;
import com.cloud_ml_app_thesis.dto.train.DeferredPredictionInput;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.service.TaskStatusService;
import com.cloud_ml_app_thesis.util.AsyncManager;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PredictionOrchestrator {

    private final TaskStatusService taskStatusService;
    private final AsyncManager asyncManager;
    private final ModelRepository modelRepository;
    private final DatasetRepository datasetRepository;

    /**
     * Handles prediction requests.
     * Returns taskId immediately after validation; MinIO upload and prediction happen async.
     */
    public String handlePrediction(ExecuteRequest request, User user) {

        // ========== VALIDATION (synchronous - throws immediately on error) ==========

        // Use eager fetch query to load all relationships upfront without transaction
        Model model = modelRepository.findByIdWithTrainingDetails(request.getModelId())
                .orElseThrow(() -> new EntityNotFoundException("Model with ID " + request.getModelId() + " not found"));

        if (model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE) &&
                !model.getTraining().getUser().getUsername().equals(user.getUsername())) {
            throw new AuthorizationDeniedException("You are not authorized to access this model");
        }

        // Validate prediction dataset (either file or datasetId required, not both)
        boolean hasFile = request.getPredictionFile() != null && !request.getPredictionFile().isEmpty();
        boolean hasDatasetId = request.getDatasetId() != null;

        if (hasFile && hasDatasetId) {
            throw new BadRequestException("Provide either predictionFile or datasetId, not both.");
        }
        if (!hasFile && !hasDatasetId) {
            throw new BadRequestException("Either predictionFile or datasetId is required for prediction.");
        }

        // Validate access if using existing dataset
        if (hasDatasetId) {
            Dataset ds = datasetRepository.findById(request.getDatasetId())
                    .orElseThrow(() -> new EntityNotFoundException("Dataset not found: " + request.getDatasetId()));
            boolean canAccess = ds.getUser().getUsername().equals(user.getUsername())
                    || ds.getAccessibility().getName().equals(DatasetAccessibilityEnum.PUBLIC);
            if (!canAccess) {
                throw new AuthorizationDeniedException("User not authorized to use this dataset");
            }
        }

        // ========== INIT TASK + SAVE TO TEMP + ASYNC (non-blocking) ==========

        String taskId = taskStatusService.initTask(TaskTypeEnum.PREDICTION, user.getUsername());
        log.info("📋 Task initialized [taskId={}] for prediction with modelId={}", taskId, request.getModelId());

        try {
            // Copy file to temp (fast local disk operation) - only if uploading file
            Path tempPrediction = hasFile ? copyToTemp(request.getPredictionFile()) : null;

            // Build deferred input
            DeferredPredictionInput input = new DeferredPredictionInput(
                    tempPrediction,
                    hasFile ? request.getPredictionFile().getOriginalFilename() : null,
                    hasFile ? request.getPredictionFile().getContentType() : null,
                    hasFile ? request.getPredictionFile().getSize() : 0,
                    request.getModelId(),
                    request.getDatasetId()  // existingDatasetId
            );

            // Fire and forget - async thread will handle MinIO upload and prediction
            asyncManager.setupAndPredict(taskId, user, input);

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
