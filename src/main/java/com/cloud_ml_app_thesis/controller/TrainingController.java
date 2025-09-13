package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.train.CustomTrainRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.request.train.TrainingStartRequest;
import com.cloud_ml_app_thesis.dto.train.TrainingDTO;
import com.cloud_ml_app_thesis.entity.ModelType;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.service.*;
import com.cloud_ml_app_thesis.util.orchestrator.TrainingOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZonedDateTime;
import java.util.List;


@RestController
@RequestMapping("api/train")
@Slf4j
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name="Training Management", description = "Endpoints to managing training")
public class TrainingController {
    private final TrainingOrchestrator orchestrator;
    private final ModelService modelService;
    private final TrainService trainService;


    @Operation(summary = "Predefined Training Management",
                description = """
                        User's Inputs :
                        'datasetFile' : training dataset(.csv)
                        'train.py' : Required structure of metrics inside train.py:
                        {
                          "type": "classification",
                          "accuracy": 0.92,
                          "precision": 0.91,
                          "recall": 0.88,
                          "f1Score": 0.89,
                          "confusionMatrix": [[30, 2], [1, 27]],
                          "classLabels": ["yes", "no"]
                        }
                        
                        {
                          "type": "regression",
                          "rmse": 1.25,
                          "mae": 0.93,
                          "rSquared": 0.85
                        }
                        
                        {
                          "type": "clustering",
                          "numClusters": 3,
                          "clusterSizes": [50, 45, 55],
                          "silhouetteScore": 0.71
                        }
                        
                        ''
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

    @GetMapping("/trainings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trainings retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server error")
    })
    @Operation(summary = "Get trainings for the current user with optional filters")
    public ResponseEntity<List<TrainingDTO>> getTrainings(
            @Parameter(description = "Start date (yyyy-MM-dd)", example = "2025-07-01")@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) Integer algorithmId,
            @RequestParam(required = false) @Parameter(description = "Type of algorithm", example = "CUSTOM") ModelTypeEnum type,
            @AuthenticationPrincipal AccountDetails accountDetails) {

        List<TrainingDTO> trainings = trainService.findTrainingsForUser(
                accountDetails.getUser(), fromDate, algorithmId, type);
        return ResponseEntity.ok(trainings);
    }

    @Operation(summary = "Delete train")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Trainings deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Server error")
    })
    @DeleteMapping("delete/{id}")
    public ResponseEntity<GenericResponse<String>> deleteTrain(@PathVariable Integer id, @AuthenticationPrincipal AccountDetails accountDetails) {
        trainService.deleteTrain(id, accountDetails.getUser());
        return ResponseEntity.accepted().body(GenericResponse.success(
                "Training deleted", " :" + id
        ));
    }
}
