package com.cloud_ml_app_thesis.dto.train;

import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.entity.ModelType;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrainingDTO {
    private Integer trainingId;
    private String algorithmName;
    private String modelType;
    private TrainingStatusEnum status;
    private ZonedDateTime startedDate;
    private ZonedDateTime finishedDate;
    private String datasetName;
}