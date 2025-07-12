package com.cloud_ml_app_thesis.dto.task;

import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AsyncTaskStatusDTO {

    private String taskId;
    private TaskTypeEnum taskType;
    private TaskStatusEnum status;
    private String resultUrl;
    private ZonedDateTime startedAt;
    private ZonedDateTime finishedAt;
    private String username;
    private Integer modelId;
    private Integer trainingId;
}