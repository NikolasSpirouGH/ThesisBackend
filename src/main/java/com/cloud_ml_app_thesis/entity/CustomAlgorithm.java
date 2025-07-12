package com.cloud_ml_app_thesis.entity;

import com.cloud_ml_app_thesis.entity.accessibility.CustomAlgorithmAccessibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "custom_algorithms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomAlgorithm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ElementCollection
    @CollectionTable(name = "custom_algorithm_keywords", joinColumns = @JoinColumn(name = "algorithm_id"))
    @Column(name = "keyword")
    private List<String> keywords;

    @ManyToOne
    @JoinColumn(name = "accessibility_id")
    private CustomAlgorithmAccessibility accessibility;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "customAlgorithm", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CustomAlgorithmImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "algorithm", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlgorithmParameter> parameters = new ArrayList<>();

}
