package com.cloud_ml_app_thesis.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "algorithm_parameters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlgorithmParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column
    private String value;

    @Column(length = 200)
    private String description;

    @Column
    private String range;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "algorithm_id")
    private CustomAlgorithm algorithm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_id")
    private CustomAlgorithmConfiguration configuration;
}
