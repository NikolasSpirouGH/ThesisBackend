package com.cloud_ml_app_thesis.entity;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import lombok.*;
import weka.core.Instances;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Builder
public class TrainingDataInput {
    private Training training;
    private Instances dataset;
    private DatasetConfiguration datasetConfiguration;
    private AlgorithmConfiguration algorithmConfiguration;
    private GenericResponse errorResponse = null;
}
