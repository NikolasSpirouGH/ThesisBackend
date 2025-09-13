package com.cloud_ml_app_thesis.entity;


import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
@Table(name = "trainings")
public class Training {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column
    private ZonedDateTime startedDate;

    @Column
    private ZonedDateTime finishedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    private TrainingStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "algorithm_configuration_id")
    @JsonIgnore
    private AlgorithmConfiguration algorithmConfiguration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_algorithm_configuration_id")
    @JsonIgnore
    private CustomAlgorithmConfiguration customAlgorithmConfiguration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private DatasetConfiguration datasetConfiguration;

    @Version
    private Integer version;

    @OneToOne(mappedBy = "training", cascade = CascadeType.ALL, orphanRemoval = true)
    private Model model;

    @Column(name = "results", length = 3000)
    private String results;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retrained_from")
    private Training retrainedFrom;

    // Optional: Validate at runtime that only one of the two algorithm configs is used
//    @PostLoad
//    @PostPersist
//    @PostUpdate
//    private void validateConsistency() {
//        if ((algorithmConfiguration != null && customAlgorithmConfiguration != null) ||
//                (algorithmConfiguration == null && customAlgorithmConfiguration == null)) {
//            throw new IllegalStateException("Training must have either algorithmConfiguration or customAlgorithmConfiguration, not both or none.");
//        }
//    }
}

