package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.model.ModelFinalizeRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.service.ModelService;
import com.cloud_ml_app_thesis.service.VisualizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/models")
@Slf4j
public class ModelController {

    private final ModelService modelService;
    private final VisualizationService visualizationService;


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

//    @GetMapping("/metrics-scatter-cluster/model/{modelId}")
//    @Operation(summary = "📊 Cluster scatter plot",
//            description = "Returns a scatter plot showing instances grouped by their assigned cluster based on the training dataset and metrics.json")
//    public ResponseEntity<ByteArrayResource> getClusterScatterPlot(
//            @PathVariable("modelId") Integer modelId,
//            @AuthenticationPrincipal AccountDetails accountDetails) {
//
//        ByteArrayResource resource = visualizationService.generateClusterScatterPlot(modelId, accountDetails.getUser());
//
//        return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cluster_scatter_plot.png")
//                .contentType(MediaType.IMAGE_PNG)
//                .body(resource);
//    }


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
    @PostMapping("/{modelId}/model")
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

    @GetMapping("/status/models-by-status")
    public ResponseEntity<GenericResponse<?>> getModelsByStatus(@PathVariable int statusId){
        return ResponseEntity.ok(new GenericResponse<>(null, null, null, null));

    }
    @GetMapping("/accessibility/model/{modelId}")
    public ResponseEntity<GenericResponse<?>> getModelAccessibilityByModelId(@PathVariable int modelId){
        return ResponseEntity.ok(new GenericResponse<>(null, null, null, null));
    }

    @GetMapping("/accessibility/{accessibilityId}")
    public ResponseEntity<GenericResponse<?>> getModelsByAccessibility(@PathVariable int accessibilityId){
        //TODO Must check if the type is "SHARED" to find all the "shared" models that Belong, or have been shared
        // to the user Requesting him
        return ResponseEntity.ok(new GenericResponse<>(null, null, null, null));
    }

    @GetMapping("/evaluation/model/{modelId}")
    public ResponseEntity<GenericResponse<?>> getEvaluationByModelId(@PathVariable int modelId){
        //TODO consider the shared problem
        return ResponseEntity.ok(new GenericResponse<>(null, null, null, null));
    }

    @GetMapping("/train/{trainingId}")
    public ResponseEntity<GenericResponse<?>> getModels(@PathVariable int trainingId){
        return ResponseEntity.ok(new GenericResponse<>(null, null, null, null));
    }
    @GetMapping("/user/{userId}")
    public ResponseEntity<GenericResponse<?>> getModelsByUser(@PathVariable String userId){
        return ResponseEntity.ok(new GenericResponse<>(null, null, null, null));
    }
    @GetMapping("/executions/model/number-of-executions/{modelId}")
    public ResponseEntity<GenericResponse<?>> getNumberOfExecutionsByModelId(@PathVariable int modelId){
        return ResponseEntity.ok(new GenericResponse<>(null, null, null, null));
    }
    @GetMapping("/executions/model/{modelId}")
    public ResponseEntity<GenericResponse<?>> getExecutionsByModelId(@PathVariable int modelId){
        //TODO consider the model access if its is shared, otherwise only the admin and the model owner can see
        return ResponseEntity.ok(new GenericResponse<>(null, null, null, null));
    }   
    public ResponseEntity<GenericResponse<?>> getModels(){
        return ResponseEntity.ok(new GenericResponse<>(null, null, null, null));
    }



}
