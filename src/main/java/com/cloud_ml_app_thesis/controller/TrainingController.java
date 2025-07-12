package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.training.CustomTrainRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.dto.request.training.TrainingStartRequest;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.service.*;
import com.cloud_ml_app_thesis.service.orchestrator.CustomTrainingOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("api/train")
@Slf4j
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Tag(name="Training Management", description = "Endpoint to managing training")
public class TrainingController {
    private final TrainService trainService;
    private final UserRepository userRepository;
    private final CustomTrainingOrchestrator orchestrator;

    ObjectMapper mapper;

    @PostMapping("/train-model")
    public ResponseEntity<GenericResponse<?>> trainModel(@ModelAttribute TrainingStartRequest request) {

        MultipartFile file = request.getFile();

        try {
            /*Path tempFile = Files.createTempFile("upload_", file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, starnd)*/
            User user = userRepository.findByUsername("bigspy").orElseThrow();
            GenericResponse<?> response = trainService.startTraining(request, user);
            if (response.getMessage() != null) {
                return ResponseEntity.internalServerError().body(response);
            }
            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(null);
        }
    }

    @PostMapping(value = "/custom", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    @Operation(
            summary = "Training Management",
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

    @GetMapping("/metrics/{modelId}")
    @Operation(summary = "Fetch metris with model")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Training metrics fetched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "500", description = "Training metrics failed to fetch")
    })
    public ResponseEntity<ByteArrayResource> getTrainingMetrics(@PathVariable Integer modelId, @AuthenticationPrincipal AccountDetails accountDetails) {

        ByteArrayResource metricsFile = trainService.getMetricsFile(modelId, accountDetails.getUser());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=metrics-" + modelId + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(metricsFile.contentLength())
                .body(metricsFile);
    }
}
