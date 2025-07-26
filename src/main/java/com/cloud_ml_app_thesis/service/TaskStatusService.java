package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.dto.task.AsyncTaskStatusDTO;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskStatusService {

    private final TaskStatusRepository taskStatusRepository;
    private final ModelMapper modelMapper;

    public AsyncTaskStatusDTO getTaskStatus(String taskId, User user) {

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        if (!task.getUsername().equals(user.getUsername())) {
            throw new AuthorizationDeniedException("You are not authorized to view this model’s metrics.");
        }
        return modelMapper.map(task, AsyncTaskStatusDTO.class);
    }

    public Integer getModelIdForTask(String taskId, User user){
        AsyncTaskStatus task = authorizeTask(taskId, user);
        return task.getModelId();
    }

    public Integer getTrainingIdForTask(String taskId, User user) {
        AsyncTaskStatus task = authorizeTask(taskId, user);
        return task.getTrainingId();
    }


    public Integer getExecutionIdForTask(String taskId, User user) {
        AsyncTaskStatus task = authorizeTask(taskId, user);
        log.info("Task id : {}", task.getTaskId());
        return task.getExecutionId();
    }

    private AsyncTaskStatus authorizeTask(String taskId, User user) {
        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Task with id " + taskId + " not found"));

        if (!task.getUsername().equals(user.getUsername())) {
            throw new AuthorizationDeniedException("Access denied to task");
        }

        if (task.getStatus() != TaskStatusEnum.COMPLETED) {
            throw new IllegalStateException("Training task is not completed");
        }
        return task;
    }

    public void initTask(String taskId, TaskTypeEnum taskType, String username) {
        AsyncTaskStatus status = AsyncTaskStatus.builder()
                .taskId(taskId)
                .taskType(taskType)
                .status(TaskStatusEnum.PENDING)
                .username(username)
                .startedAt(ZonedDateTime.now())
                .build();

        taskStatusRepository.save(status);
        log.info("[TASK INIT] [{}] [{}] by user={}", taskType, taskId, username);
    }

    public void completeTask(String taskId) {
        taskStatusRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus(TaskStatusEnum.COMPLETED);
            task.setFinishedAt(ZonedDateTime.now());
            taskStatusRepository.save(task);
            log.info("Task completed [{}]", taskId);
        });
    }

    public void taskFailed(String taskId, String errorMessage) {
        taskStatusRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus(TaskStatusEnum.FAILED);
            task.setErrorMessage(errorMessage);
            task.setFinishedAt(ZonedDateTime.now());
            taskStatusRepository.save(task);
            log.info("Task failed [{}]", taskId);
        });
    }
}

