package com.cloud_ml_app_thesis.dto.train;

import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MyTrainingDTO {
    private Integer id;
    private ZonedDateTime startedDate;
    private ZonedDateTime finishedDate;
    private TrainingStatusEnum status;
    private String basicAttributesColumns;
    private String targetClassColumn;
    private String algorithmOptions;
    private Integer modelId;
//    private String results;
}
