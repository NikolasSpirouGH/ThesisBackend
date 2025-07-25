package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.train.CustomTrainRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.request.train.TrainingStartRequest;
import com.cloud_ml_app_thesis.service.*;
import com.cloud_ml_app_thesis.util.orchestrator.TrainingOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/train")
@Slf4j
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name="Training Management", description = "Endpoints to managing training")
public class TrainingController {
    private final TrainingOrchestrator orchestrator;
    private final PredefinedTrainService predefinedTrainService;
    private final VisualizationService visualizationService;


    @Operation(summary = "Predefined Training Management",
                description = """
                        User's Inputs :
                        'datasetFile' : training dataset(.csv)
                        'some other inputs' : ..input'
                        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation =   TrainingStartRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Training task started successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "500", description = "Training task failed to start")
    })
    @PostMapping("/train-model")
    public ResponseEntity<GenericResponse<?>> trainModel(@Valid @ModelAttribute TrainingStartRequest request, @AuthenticationPrincipal AccountDetails accountDetails) {

            String taskId = orchestrator.handleTrainingRequest(request, accountDetails.getUser());
            return ResponseEntity.accepted().body(GenericResponse.success(
                    "Training started asynchronously. Use taskId to track progress.",
                    taskId
            ));
    }

    @PostMapping(value = "/custom", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "User Defined Training Management",
            description = """
        Ο χρήστης οφείλει να παρέχει:
        - `datasetFile`: training dataset (.csv)
        - `parametersFile`: optional params.json
        - `basicAttributesColumns`: comma-separated feature columns
        - `targetColumn`: target class
    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = CustomTrainRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Training task started successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "500", description = "Training task failed to start")
    })
    public ResponseEntity<GenericResponse<String>> trainCustomModel(
            @Valid @ModelAttribute CustomTrainRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails) {

        String taskId = orchestrator.handleCustomTrainingRequest(request, accountDetails);

        return ResponseEntity.accepted().body(GenericResponse.success(
                "Training started asynchronously. Use taskId to track progress.",
                taskId
        ));
    }



}
