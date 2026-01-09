package com.cloud_ml_app_thesis.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "algorithms", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Algorithm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(length = 5000)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "type_id", nullable = false)
    private AlgorithmType type;

    @Column(length = 5000)
    private String options;

    @Column(length = 5000)
    private String optionsDescription;

    @Column(length = 5000)
    private String defaultOptions;

    @Column(name = "class_name")
    private String className;

}
