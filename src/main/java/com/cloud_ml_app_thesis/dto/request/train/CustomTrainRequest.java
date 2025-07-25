package com.cloud_ml_app_thesis.dto.request.train;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@Schema(name = "CustomTrainRequest", description = "Request to train a model with a user-defined algorithm using a dataset and optional parameters")
public class CustomTrainRequest {

    @NotNull
    @Schema(
            description = "ID of the user-defined algorithm to train",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Integer algorithmId;

    @NotNull
    @Schema(
            description = "Training dataset file (.csv, .arff, or .xlsx). Will be mounted at `/data/dataset.csv` inside the container.",
            type = "string",
            format = "binary",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private MultipartFile datasetFile;

    @Schema(
            description = "Optional parameters JSON file to override default algorithm parameters. Will be mounted at `/data/params.json`.",
            type = "string",
            format = "binary",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            example = "params.json"
    )
    private MultipartFile parametersFile;

    @Schema(
            description = """
            Comma-separated list of feature columns to use during training (optional).
            If not provided, all columns except the last will be used.
            Example: "age,income,balance"
        """,
            example = "feature1,feature2,feature3"
    )
    private String basicAttributesColumns;

    @Schema(
            description = """
            The target (class) column to predict (optional).
            If not provided, the last column of the dataset will be assumed.
        """,
            example = "class"
    )
    private String targetColumn;
}