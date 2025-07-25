package com.cloud_ml_app_thesis.entity.model;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.status.ModelExecutionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Entity
@Table(name="models_executions")
@Setter
@Getter
public class ModelExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "model_id")
    private Model model;

    private ZonedDateTime executedAt;

    @ManyToOne
    @JoinColumn(name = "status_id")
    private ModelExecutionStatus status;

    @Column(length = 5000)
    private String predictionResult;

    @ManyToOne
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    @ManyToOne
    @JoinColumn(name="executed_by_user_id", nullable = false)
    private User executedByUser;

}
