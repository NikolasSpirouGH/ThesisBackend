package com.cloud_ml_app_thesis.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.execution.ExecuteRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.execution.ModelExecutionDTO;
import com.cloud_ml_app_thesis.service.ModelExecutionService;
import com.cloud_ml_app_thesis.util.orchestrator.PredictionOrchestrator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/model-exec")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Model Execution of Weka and User defined Models", description = "Endpoints to manage model execution")
public class ModelExecutionController {

    private final ModelExecutionService modelExecutionService;
    private final PredictionOrchestrator predictionOrchestrator;

    @PostMapping("/execute")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Execute model (Weka or Custom)",
            description = """
            Ο χρήστης οφείλει να παρέχει:
            - `modelId`: ID του μοντέλου (Weka-based ή Custom Docker)
            - `predictionFile`: αρχείο πρόβλεψης (.csv)
            - '(Optional) targetColumn' : targetColumn
            - '(Optional) attributes' : attributes
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation =   ExecuteRequest.class)
                    )
            ))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Model execution task started successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "500", description = "Model execution task failed to start")
    })
    public ResponseEntity<GenericResponse<String>> executeModel(
            @AuthenticationPrincipal AccountDetails accountDetails,
            @Valid @ModelAttribute ExecuteRequest executeRequest) {

        String taskId = predictionOrchestrator.handlePrediction(executeRequest, accountDetails.getUser());

        return ResponseEntity.accepted().body(GenericResponse.success(
                "Prediction started asynchronously. Track with taskId.",
                taskId
        ));
    }

    @GetMapping("/list")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List user's model executions",
            description = "Retrieves all model executions (both Weka and Custom) for the authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Executions retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<GenericResponse<List<ModelExecutionDTO>>> listUserExecutions(
            @AuthenticationPrincipal AccountDetails accountDetails) {

        log.info("📋 Listing executions for user: {}", accountDetails.getUsername());

        List<ModelExecutionDTO> executions = modelExecutionService.getUserExecutions(accountDetails.getUser());

        log.info("✅ Retrieved {} executions for user: {}", executions.size(), accountDetails.getUsername());

        return ResponseEntity.ok(GenericResponse.success(
                "Executions retrieved successfully",
                executions
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

    @DeleteMapping("/{executionId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Delete model execution",
            description = "Deletes a model execution and its associated prediction result file.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Execution deleted successfully"),
            @ApiResponse(responseCode = "403", description = "User not authorized to delete this execution"),
            @ApiResponse(responseCode = "404", description = "Execution not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<GenericResponse<Void>> deleteExecution(
            @PathVariable Integer executionId,
            @AuthenticationPrincipal AccountDetails accountDetails) {

        log.info("🗑️ Delete request for model execution [executionId={}, user={}]",
                executionId, accountDetails.getUsername());

        modelExecutionService.deleteExecution(executionId, accountDetails.getUser());

        log.info("✅ Execution deleted successfully [executionId={}]", executionId);

        return ResponseEntity.ok(GenericResponse.success(
                "Execution deleted successfully",
                null
        ));
    }

}
