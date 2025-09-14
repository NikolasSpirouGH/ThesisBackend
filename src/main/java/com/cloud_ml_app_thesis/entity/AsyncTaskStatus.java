package com.cloud_ml_app_thesis.entity;

import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AsyncTaskStatus {

    @Id
    private String taskId;

    @Enumerated(EnumType.STRING)
    private TaskTypeEnum taskType; // TRAINING, PREDICTION

    @Enumerated(EnumType.STRING)
    private TaskStatusEnum status; // PENDING, RUNNING, COMPLETED, FAILED

    private String errorMessage;

    @Version
    private Integer version;

    private ZonedDateTime startedAt;
    private ZonedDateTime finishedAt;

    private String username;

    private Integer modelId;
    private Integer trainingId;
    private Integer executionId;
    @Column(nullable = false)
    private boolean stopRequested;


}