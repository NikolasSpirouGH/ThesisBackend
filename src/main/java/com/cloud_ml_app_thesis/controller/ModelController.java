package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.model.ModelDTO;
import com.cloud_ml_app_thesis.dto.request.model.ModelFinalizeRequest;
import com.cloud_ml_app_thesis.dto.request.model.ModelUpdateRequest;
import com.cloud_ml_app_thesis.dto.request.model.UpdateModelContentRequest;
import com.cloud_ml_app_thesis.dto.request.model.ModelSearchRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.service.ModelService;
import com.cloud_ml_app_thesis.service.VisualizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/models")
@Slf4j
public class ModelController {

    private final ModelService modelService;
    private final VisualizationService visualizationService;

    @Operation(summary = "Get all accessible models", description = "Returns all models owned by the user or publicly accessible")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Models retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ModelDTO>> getAccessibleModels(@AuthenticationPrincipal AccountDetails accountDetails) {
        User user = accountDetails.getUser();
        boolean isAdmin = accountDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN") || auth.getAuthority().equals("ROLE_ADMIN"));

        List<ModelDTO> models = isAdmin
                ? modelService.getAllModels()
                : modelService.getAccessibleModels(user);
        return ResponseEntity.ok(models);
    }

    @GetMapping("/metrics/{modelId}")
    @Operation(summary = "Fetch metris with model")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Training metrics fetched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "500", description = "Training metrics failed to fetch")
    })
    public ResponseEntity<ByteArrayResource> getTrainingMetrics(@PathVariable Integer modelId, @AuthenticationPrincipal AccountDetails accountDetails) {

        ByteArrayResource metricsFile = modelService.getMetricsFile(modelId, accountDetails.getUser());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=metrics-" + modelId + ".json")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(metricsFile.contentLength())
                .body(metricsFile);
    }

    //Classification
    @Operation(summary = "Model Evaluation Chart", description = "Returns PNG bar chart with accuracy, precision, recall, F1 from metrics.json")
    @GetMapping(value = "/metrics-bar/model/{modelId}", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ByteArrayResource> getMetricsBarChart(@PathVariable Integer modelId, @AuthenticationPrincipal AccountDetails accountDetails) {
        return ResponseEntity.ok(visualizationService.generateBarChartFromMetricsJson(modelId, accountDetails.getUser()));
    }

    //Classification
    @Operation(summary = "Confusion Matrix", description = "Returns PNG of confusion matrix chart")
    @GetMapping(value = "/metrics-confusion/model/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<ByteArrayResource> getConfusionMatrixChart(
            @PathVariable("id") Integer id,
            @AuthenticationPrincipal AccountDetails user) {

        //We have to check if model has been produced by Classification and no Regression
        ByteArrayResource image = visualizationService.generateConfusionMatrixChart(id, user.getUser());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(image);
    }
    //Regression
    @GetMapping(value = "/metrics-scatter/model/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Regression Scatter Plot", description = "Returns PNG of actual vs predicted plot for regression model")
    public ResponseEntity<ByteArrayResource> getRegressionScatterPlot(
            @PathVariable("id") Integer id,
            @AuthenticationPrincipal AccountDetails user) {
        ByteArrayResource image = visualizationService.generateRegressionScatterPlot(id, user.getUser());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
    }

    @GetMapping(value = "/metrics-residual/model/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Regression Scatter Plot", description = "Returns PNG of residuals vs predicted plot for regression model")
    public ResponseEntity<ByteArrayResource> getResidualPlot(
            @PathVariable("id") Integer id,
            @AuthenticationPrincipal AccountDetails user) {
        ByteArrayResource image = visualizationService.generateResidualPlot(id, user.getUser());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
    }

    //Clusterer
    @Operation(summary = "Cluster Sizes Chart", description = "Returns PNG bar chart with the size of each cluster")
    @GetMapping(value = "/metrics-cluster-sizes/model/{modelId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<ByteArrayResource> getClusterSizesChart(
            @PathVariable("modelId") Integer modelId,
            @AuthenticationPrincipal AccountDetails accountDetails) {
        ByteArrayResource image = visualizationService.generateClusterSizeChart(modelId, accountDetails.getUser());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
    }

    @GetMapping("/metrics-scatter-cluster/model/{modelId}")
    @Operation(summary = "📊 Cluster scatter plot",
            description = "Returns a scatter plot showing instances grouped by their assigned cluster based on the training dataset and metrics.json")
    public ResponseEntity<ByteArrayResource> getClusterScatterPlot(
            @PathVariable("modelId") Integer modelId,
            @AuthenticationPrincipal AccountDetails accountDetails) {

        ByteArrayResource resource = visualizationService.generateClusterScatterPlot(modelId, accountDetails.getUser());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cluster_scatter_plot.png")
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }


    @Operation(
            summary = "Returns the status of a Model.",
            description = "Giving the modelId, the endpoint will return the status of the Model."
    )
    @ApiResponse(
            responseCode = "200",
            description = "A Generic Response object in JSON format.",
            content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = ApiResponse.class))
            )
    )
    @GetMapping(path = "/status/{modelId}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<GenericResponse<?>> getModelStatus(@PathVariable int modelId) {
        return ResponseEntity.ok(new GenericResponse<>(null, null, null, null));
    }

    @Operation(
            summary = "Finalize a model from a completed training",
            description = "Finalizes the model associated with a completed training by setting metadata, access, and category."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Model finalized successfully",
                    content = @Content(schema = @Schema(implementation = GenericResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthorized access or training does not belong to user",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Training or category not found",
                    content = @Content)
    })
    @PostMapping("/finalize/{modelId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<Integer>> finalizeModelFromTraining(
            @PathVariable Integer modelId,
            @RequestBody ModelFinalizeRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("🔐 Finalizing model from trainingId={} by user={}", modelId, userDetails.getUsername());
        modelService.finalizeModel(modelId, userDetails, request);
        return ResponseEntity.ok(new GenericResponse<>(modelId, null, "✅ Model finalized successfully", null));
    }

    @Operation(summary = "Download trained model by trainingId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Model downloaded successfully"),
            @ApiResponse(responseCode = "404", description = "Training or model not found"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "409", description = "Training not completed")
    })
    @GetMapping("/training/{trainingId}/download-model")
    public ResponseEntity<ByteArrayResource> downloadModel(
            @PathVariable Integer trainingId,
            @AuthenticationPrincipal AccountDetails accountDetails) {

        ByteArrayResource model = modelService.downloadModel(trainingId, accountDetails.getUser());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + accountDetails.getUser().getUsername() + "_" + "model" + trainingId + ".pkl")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(model.contentLength())
                .body(model);
    }

    @Operation(summary = "Get model by ID", description = "Returns a single model's details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Model retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Model not found"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/{modelId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ModelDTO> getModelById(
            @PathVariable Integer modelId,
            @AuthenticationPrincipal AccountDetails accountDetails) {
        ModelDTO model = modelService.getModelById(modelId, accountDetails.getUser());
        return ResponseEntity.ok(model);
    }

    @Operation(summary = "Update model", description = "Updates a model's metadata (owner only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Model updated successfully"),
            @ApiResponse(responseCode = "404", description = "Model not found"),
            @ApiResponse(responseCode = "403", description = "Access denied - not owner")
    })
    @PutMapping("/update/{modelId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<Void>> updateModel(
            @PathVariable Integer modelId,
            @RequestBody ModelUpdateRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails) {
        log.info("Updating model with ID={} by user={}", modelId, accountDetails.getUsername());
        modelService.updateModel(modelId, request, accountDetails.getUser());
        return ResponseEntity.ok(new GenericResponse<>(null, null, "Model updated successfully", null));
    }

    @Operation(
            summary = "Update model content",
            description = "Replaces the model's training and content with results from a new training (owner only). " +
                    "This allows you to 'upgrade' a model to use better training results while keeping the same model ID and metadata."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Model content updated successfully"),
            @ApiResponse(responseCode = "404", description = "Model or training not found"),
            @ApiResponse(responseCode = "403", description = "Access denied - not owner of model or training"),
            @ApiResponse(responseCode = "400", description = "Training not completed or already used by another model")
    })
    @PatchMapping("/updateContent/{modelId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<Void>> updateModelContent(
            @PathVariable Integer modelId,
            @Valid @RequestBody UpdateModelContentRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails) {
        log.info("Updating model content: modelId={}, newTrainingId={}, user={}",
                modelId, request.getNewTrainingId(), accountDetails.getUsername());
        modelService.updateModelContent(modelId, request.getNewTrainingId(), accountDetails.getUser());
        return ResponseEntity.ok(new GenericResponse<>(null, null, "Model content updated successfully", null));
    }

    @Operation(summary = "Delete model", description = "Deletes a model (owner only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Model deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Model not found"),
            @ApiResponse(responseCode = "403", description = "Access denied - not owner")
    })
    @DeleteMapping("/delete/{modelId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<Void>> deleteModel(
            @PathVariable Integer modelId,
            @AuthenticationPrincipal AccountDetails accountDetails) {
        log.info("Deleting model with ID={} by user={}", modelId, accountDetails.getUsername());
        modelService.deleteModel(modelId, accountDetails.getUser());
        return ResponseEntity.ok(new GenericResponse<>(null, null, "Model deleted successfully", null));
    }

    @Operation(summary = "Search models", description = "Search models with various criteria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search completed successfully")
    })
    @PostMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ModelDTO>> searchModels(
            @Valid @RequestBody ModelSearchRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails) {
        log.info("Searching models for user={}", accountDetails.getUsername());
        List<ModelDTO> models = modelService.searchModels(request, accountDetails.getUser());
        return ResponseEntity.ok(models);
    }
}
