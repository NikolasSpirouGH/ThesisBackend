package com.cloud_ml_app_thesis.dto.train;

import com.cloud_ml_app_thesis.entity.Training;
import weka.core.Instances;

public record PredefinedTrainMetadata(
        Integer trainingId,
        Instances dataset,
        Integer datasetConfigurationId,
        Integer algorithmConfigurationId
) {}