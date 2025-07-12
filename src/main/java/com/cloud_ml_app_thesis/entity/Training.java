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

    @ManyToOne
    @JoinColumn(name = "status_id")
    @Enumerated(EnumType.STRING)
    private TrainingStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="algorithm_id")
    @JsonIgnore
    private AlgorithmConfiguration algorithmConfiguration;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name="user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_algorithm_id")
    @JsonIgnore
    private CustomAlgorithmConfiguration customAlgorithmConfiguration;

    @ManyToOne
    @JoinColumn(name="dataset_id")
    private DatasetConfiguration datasetConfiguration;

    @OneToOne(mappedBy = "training")
    private Model model;

    @Column(name = "results", length = 3000)
    private String results;
}

