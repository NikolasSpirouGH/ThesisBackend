package com.cloud_ml_app_thesis.dto.response;

import com.cloud_ml_app_thesis.dto.custom_algorithm.AlgorithmParameterDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response DTO for user-defined machine learning algorithms")
public class AlgorithmResponseDTO {

    @Schema(description = "Unique identifier of the algorithm", example = "42")
    private Long id;

    @Schema(description = "Name of the algorithm", example = "Custom XGBoost")
    private String name;

    @Schema(description = "Textual description of the algorithm", example = "XGBoost implementation for binary classification")
    private String description;

    @Schema(description = "Version string of the algorithm", example = "1.0.0")
    private String version;

    @Schema(description = "True if the algorithm is public, false if it's private")
    private Boolean isPublic;

    @Schema(description = "List of keyword tags associated with the algorithm")
    private List<String> keywords;

    @Schema(description = "Reference to the Docker image, either MinIO key or DockerHub URL")
    private String dockerImageRef;

    @Schema(description = "List of configuration parameters for the algorithm")
    private List<AlgorithmParameterDTO> parameters;

    @Schema(description = "Timestamp when the algorithm was created")
    private LocalDateTime createdAt;

    @Schema(description = "Username of the creator of the algorithm")
    private String ownerUsername;
}
