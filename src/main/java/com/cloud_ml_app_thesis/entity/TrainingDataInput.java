package com.cloud_ml_app_thesis.entity;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import lombok.*;
import weka.core.Instances;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class TrainingDataInput {
    private Training training;
    private Instances dataset; // OK
    private String filename; // OK
    private DatasetConfiguration datasetConfiguration; // OK
//    private String algorithmClassName; //commented because we can do algorithmConfiguration.getAlgorithm().getClassName();
    private AlgorithmConfiguration algorithmConfiguration; // OK
    private GenericResponse errorResponse = null;
}
