package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.execution.CustomExecuteRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.service.DatasetService;
import com.cloud_ml_app_thesis.service.ModelExecutionService;
import com.cloud_ml_app_thesis.service.orchestrator.CustomPredictionOrchestrator;
import com.cloud_ml_app_thesis.util.AsyncManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.internal.inject.Custom;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZonedDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/model-exec")
@RequiredArgsConstructor
@Slf4j
public class ModelExecutionController {

    private final ModelExecutionService modelExecutionService;
    private final TaskStatusRepository taskStatusRepository;
    private final AsyncManager asyncManager;
    private final DatasetService datasetService;
    private final CustomPredictionOrchestrator customPredictionOrchestrator;

    @PostMapping("/execute")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<ByteArrayResource>> executeModel(@AuthenticationPrincipal UserDetails userDetails, @NotBlank @PathVariable Integer modelId,
                                                           @NotNull @RequestParam MultipartFile predictDataset, @RequestParam String targetUsername) {
        try {
            ByteArrayResource predictionFile = modelExecutionService.executeModel(userDetails, modelId, predictDataset, targetUsername);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"predictions.arff\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new GenericResponse<ByteArrayResource>(predictionFile, null, null, new Metadata()));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }


    @PostMapping(value = "/execute-custom", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Execute prediction using custom Docker algorithm model",
            description = """
                Ο χρήστης οφείλει να παρέχει:
                - `modelId`: ID του custom model
                - `predictionFile`: prediction dataset (.csv)
                - '(Optional) targetColumn' : targetColumn
                - '(Optional) attributes' : attributes
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = CustomExecuteRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Model execution task started successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "500", description = "Model execution task failed to start")
    })
    public ResponseEntity<GenericResponse<String>> executeCustomModel(
            @Valid @ModelAttribute CustomExecuteRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {

        String taskId = customPredictionOrchestrator.handleCustomPrediction(request, accountDetails.getUser());

        return ResponseEntity.accepted().body(GenericResponse.success(
                "Prediction started asynchronously. Track with taskId.",
                taskId
        ));
    }

    @GetMapping("/{executionId}/result")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Download prediction result file",
            description = "Downloads the prediction result generated by a custom model execution.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Prediction result downloaded successfully"),
            @ApiResponse(responseCode = "403", description = "User not authorized to access this result"),
            @ApiResponse(responseCode = "404", description = "Execution not found or no result available"),
            @ApiResponse(responseCode = "500", description = "Internal server error during result retrieval")
    })
    public ResponseEntity<Resource> downloadExecutionResult(
            @PathVariable Integer executionId,
            @AuthenticationPrincipal AccountDetails accountDetails) {

        log.info("📥 Download request for model execution result [executionId={}, user={}]",
                executionId, accountDetails.getUsername());

        ByteArrayResource resource = modelExecutionService.getExecutionResultsFile(
                executionId, accountDetails.getUser());

        log.info("✅ Prediction result retrieved successfully [executionId={}]", executionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"prediction-result.csv\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(resource.contentLength())
                .body(resource);
    }

}
