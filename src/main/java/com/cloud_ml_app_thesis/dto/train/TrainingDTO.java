package com.cloud_ml_app_thesis.dto.train;

import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;

import java.time.ZonedDateTime;

public class TrainingDTO {
    private Integer id;
    private ZonedDateTime startedDate;
    private ZonedDateTime finishedDate;
    private TrainingStatusEnum status;
    private AlgorithmConfiguration algorithmConfiguration;
    private DatasetConfiguration datasetConfiguration;
    private String username;
    private String modelId;
    private String results;
}
