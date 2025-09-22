package com.cloud_ml_app_thesis.dto.train;

import java.util.List;

public record RetrainOptionsDTO(
        List<RetrainTrainingOptionDTO> trainings,
        List<RetrainModelOptionDTO> models
) {}
