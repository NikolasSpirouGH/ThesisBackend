package com.cloud_ml_app_thesis.util.orchestrator;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Component;

import com.cloud_ml_app_thesis.dto.request.execution.ExecuteRequest;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.service.DatasetService;
import com.cloud_ml_app_thesis.util.AsyncManager;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PredictionOrchestrator {

    private final DatasetService datasetService;
    private final TaskStatusRepository taskStatusRepository;
    private final AsyncManager asyncManager;
    private final ModelRepository modelRepository;

    public String handlePrediction(ExecuteRequest request, User user) {

        // Use eager fetch query to load all relationships upfront without transaction
        Model model = modelRepository.findByIdWithTrainingDetails(request.getModelId())
                .orElseThrow(() -> new EntityNotFoundException("Model with ID " + request.getModelId() + " not found"));

        if (model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE) &&
                !model.getTraining().getUser().getUsername().equals(user.getUsername())) {
            throw new AuthorizationDeniedException("You are not authorized to access this model");
        }

        String taskId = UUID.randomUUID().toString();

        String datasetKey = datasetService.uploadPredictionFile(request.getPredictionFile(), user);

        log.info("Upload prediction file: {}", datasetKey);
        AsyncTaskStatus task = AsyncTaskStatus.builder()
                .taskId(taskId)
                .taskType(TaskTypeEnum.PREDICTION)
                .status(TaskStatusEnum.PENDING)
                .startedAt(ZonedDateTime.now())
                .username(user.getUsername())
                .build();
        taskStatusRepository.save(task);

        switch (model.getModelType().getName()) {
            case CUSTOM -> asyncManager.predictCustom(taskId, request.getModelId(), datasetKey, user);
            case PREDEFINED -> asyncManager.predictPredefined(taskId, request.getModelId(), datasetKey, user);
            default -> throw new IllegalArgumentException("Unsupported algorithm type: " + model.getModelType().getName());
        }

        return taskId;
    }
}
