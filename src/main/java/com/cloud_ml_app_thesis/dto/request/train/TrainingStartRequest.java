package com.cloud_ml_app_thesis.dto.request.train;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@RequiredArgsConstructor
public class TrainingStartRequest {

    @Schema(description = "PLease provide us dataset file")
    private MultipartFile file;

    @Pattern(regexp = "^(\\d+|\\d+(,\\d+)+)?$", message = "basicCharacteristicsColumns must be numbers or numbers followed by commas")
    @Schema(description = "Please provide the attributes from the dataset that you want to start the training with")
    private String basicCharacteristicsColumns;

    @Pattern(regexp = "^\\d*$", message = "targetClassColumn must be a number.")
    @Schema(description = "Please provide the number of the column that is the class, by default will be the last one")
    private String targetClassColumn;

    @Pattern(regexp = "^\\d+$", message = "algorithmId must be a number")
    @Positive(message = "algorithmId must be greater than zero")
    @Schema(description = "Please choose algorithm id from algorithms table")
    private String algorithmId;

    private String options;

    @Pattern(regexp = "^\\d+$", message = "algorithmConfigurationId must be a number")
    @Positive(message = "algorithmConfigurationId must be greater than zero")
    @Schema(description = "Please choose configured algorithm id from algorithms table")
    private String algorithmConfigurationId;

    @Pattern(regexp = "^\\d+$", message = "datasetId must be a number")
    @Positive(message = "datasetId must be greater than zero")
    private String datasetId;

    @Pattern(regexp = "^\\d+$", message = "datasetConfigurationId must be a number")
    @Positive(message = "datasetConfigurationId must be greater than zero")
    private String datasetConfigurationId;

    private String trainingId;

    private String modelId;

}