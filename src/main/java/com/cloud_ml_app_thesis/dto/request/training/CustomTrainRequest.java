package com.cloud_ml_app_thesis.dto.request.training;

import com.cloud_ml_app_thesis.validation.validation.ValidImageSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Data
@Builder
@Schema(description = "Request to train a model with a user-defined algorithm")
public class CustomTrainRequest {


    @NotNull
    @Schema(description = "ID of the user-defined algorithm to train", example = "1")
    private Integer algorithmId;

    @NotNull
    @Schema(description = "Training dataset file", type = "string", format = "binary")
    private MultipartFile datasetFile;

    @Schema(description = "Optional JSON for parameter overrides (if needed)")
    private Map<String, String> parameters;

    @Schema(description = "DatasetConfiguration ID")
    private Integer datasetConfigurationId;

    @Schema(description = "The columns of dataset that user wants to train his algorithm")
    private String basicAttributesColumns;

    @Schema(description = "The class column")
    private String targetColumn;
}
