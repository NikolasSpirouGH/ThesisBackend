package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.model.ModelFinalizeRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.service.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @PostMapping("/{trainingId}/model")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<Integer>> finalizeModelFromTraining(
            @PathVariable Integer trainingId,
            @RequestBody ModelFinalizeRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("🔐 Finalizing model from trainingId={} by user={}", trainingId, userDetails.getUsername());
        Integer modelId = modelService.finalizeModel(trainingId, userDetails, request);
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
