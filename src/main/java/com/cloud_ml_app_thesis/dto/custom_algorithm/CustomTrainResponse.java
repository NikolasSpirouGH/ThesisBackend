package com.cloud_ml_app_thesis.dto.custom_algorithm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema (description = "Response after")
public class CustomTrainResponse {


    private String taskId;

    @Schema(description = "Unique identifier of the trained model")
    private Integer modelId;

    @Schema(description = "Path to the stored trained model (MinIO key)")
    private String modelPath;

    @Schema(description = "Timestamp of training")
    private LocalDateTime trainedAt;
}
