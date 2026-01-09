package com.cloud_ml_app_thesis.entity.model;

import com.cloud_ml_app_thesis.entity.ModelType;
import com.cloud_ml_app_thesis.entity.Category;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.entity.accessibility.ModelAccessibility;
import com.cloud_ml_app_thesis.entity.status.ModelStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "models")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Model {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(optional = false)
    @JoinColumn(name = "training_id", nullable = false)
    private Training training;

    @Column(name = "model_url", length = 1000)
    private String modelUrl;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "model_type_id", nullable = false)
    private ModelType modelType;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "status_id")
    private ModelStatus status;

    @Version
    @Column
    private Integer version;

    @ManyToOne
    @JoinColumn(name = "accessibility_id")
    private ModelAccessibility accessibility;

    @Column(name = "model_name")
    private String name;

    @Column(name = "model_description")
    @Size(max = 500)
    private String description;

    @Column(name = "data_description")
    @Size(max = 500)
    private String dataDescription;

    @ElementCollection
    @CollectionTable(name = "model_keywords", joinColumns = @JoinColumn(name = "model_id"))
    @Column(name = "keyword")
    private List<String> keywords;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "finalized")
    private boolean finalized;

    @Column
    private ZonedDateTime finalizationDate;

    // Main category (required)
    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @OneToMany(mappedBy = "model", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ModelExecution> executions;

    @OneToMany(mappedBy = "model")
    private List<ModelShare> shares = new ArrayList<>();

    @Column(name = "metrics_url")
    private String metricsUrl;

    @Column(name = "label_mapping_url", length = 1000)
    private String labelMappingUrl;

    @Column(name = "feature_columns_url", length = 1000)
    private String featureColumnsUrl;
}