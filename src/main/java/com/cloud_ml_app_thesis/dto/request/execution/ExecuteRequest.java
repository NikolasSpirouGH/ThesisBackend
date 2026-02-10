package com.cloud_ml_app_thesis.dto.request.execution;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "Prediction request using model and prediction dataset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecuteRequest {

    @Schema(description = "Model ID", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer modelId;

    @Schema(description = "Prediction dataset (.csv)", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private MultipartFile predictionFile;

    @Schema(description = "ID of existing training dataset for prediction. Alternative to predictionFile.", example = "10")
    private Integer datasetId;

}
