package com.cloud_ml_app_thesis.dto.request.training;


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

    private MultipartFile file;

    @Pattern(regexp = "^(\\d+|\\d+(,\\d+)+)?$", message = "basicCharacteristicsColumns must be numbers or numbers followed by commas")
    private String basicCharacteristicsColumns;

    @Pattern(regexp = "^\\d*$", message = "targetClassColumn must be a number.")
    private String targetClassColumn;

    @Pattern(regexp = "^\\d+$", message = "algorithmId must be a number")
    @Positive(message = "algorithmId must be greater than zero")
    private String algorithmId;

    private String algorithmOptions;

    @Pattern(regexp = "^\\d+$", message = "algorithmConfigurationId must be a number")
    @Positive(message = "algorithmConfigurationId must be greater than zero")
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