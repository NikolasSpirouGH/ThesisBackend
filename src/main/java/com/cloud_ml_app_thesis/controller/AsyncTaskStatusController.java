package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.task.AsyncTaskStatusDTO;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.service.TaskStatusService;
import com.cloud_ml_app_thesis.service.TrainService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Monitor Tasks", description = "Track status of training and prediction tasks")
public class AsyncTaskStatusController {

    private final TaskStatusService taskStatusService;
    private final TaskStatusRepository taskStatusRepository;
    private final TrainService trainService;

    @Operation(summary = "Tracking training")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Training monitored successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "500", description = "Training monitoring failed")
    })
    @GetMapping("/{trackingId}")
    public ResponseEntity<AsyncTaskStatusDTO> getStatus(@PathVariable String trackingId, @AuthenticationPrincipal AccountDetails accountDetails) {
        AsyncTaskStatusDTO response = taskStatusService.getTaskStatus(trackingId, accountDetails.getUser());
        log.info(response.toString());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Return modelId produced by completed training task")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "modelId returned"),
            @ApiResponse(responseCode = "404", description = "Task not found"),
            @ApiResponse(responseCode = "409", description = "Task not yet completed")
    })
    @GetMapping("/{taskId}/model-id")
    public ResponseEntity<GenericResponse<Map<String, Integer>>> getModelId(@PathVariable String taskId, @AuthenticationPrincipal AccountDetails accountDetails) {
        Integer modelId = taskStatusService.getModelIdForTask(taskId, accountDetails.getUser());
        Map<String, Integer> responseBody = Map.of("modelId", modelId);
        GenericResponse<Map<String, Integer>> response = GenericResponse.success("Model ID retrieved successfully", responseBody);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Return trainingId associated with completed training task")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "trainingId returned"),
            @ApiResponse(responseCode = "404", description = "Task not found"),
            @ApiResponse(responseCode = "409", description = "Task not yet completed")
    })
    @GetMapping("/{taskId}/training-id")
    public ResponseEntity<GenericResponse<Map<String, Integer>>> getTrainingId(
            @PathVariable String taskId,
            @AuthenticationPrincipal AccountDetails accountDetails) {

        Integer trainingId = taskStatusService.getTrainingIdForTask(taskId, accountDetails.getUser());
        Map<String, Integer> responseBody = Map.of("trainingId", trainingId);
        GenericResponse<Map<String, Integer>> response = GenericResponse.success("Training ID retrieved successfully", responseBody);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Return executionId associated with completed training task")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "executionId returned"),
            @ApiResponse(responseCode = "404", description = "Task not found"),
            @ApiResponse(responseCode = "409", description = "Task not yet completed")
    })
    @GetMapping("/{taskId}/execution-id")
    public ResponseEntity<GenericResponse<Map<String, Integer>>> getExecutionId(
            @PathVariable String taskId,
            @AuthenticationPrincipal AccountDetails accountDetails) {

        Integer executionId = taskStatusService.getExecutionIdForTask(taskId, accountDetails.getUser());
        log.info("Execution id : {}", executionId);
        Map<String, Integer> responseBody = Map.of("executionId", executionId);
        GenericResponse<Map<String, Integer>> response = GenericResponse.success("Execution ID retrieved successfully", responseBody);
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Stop task",
            description = "User-initiated stop request for a running task")
    @PutMapping("/stop/{taskId}")
    public ResponseEntity<GenericResponse<String>> stopTask(@PathVariable String taskId, @AuthenticationPrincipal AccountDetails accountDetails) throws InterruptedException {
        taskStatusService.stopTask(taskId, accountDetails.getUser().getUsername());
        return ResponseEntity.ok(GenericResponse.success("Training stop requested", taskId));
    }
}
