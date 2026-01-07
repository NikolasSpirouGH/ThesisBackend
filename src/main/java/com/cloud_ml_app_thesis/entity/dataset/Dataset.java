package com.cloud_ml_app_thesis.entity.dataset;


import com.cloud_ml_app_thesis.entity.Category;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.accessibility.DatasetAccessibility;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name="datasets")
public class Dataset {
    //CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "datasets_id_seq")
    @SequenceGenerator(name = "datasets_id_seq", sequenceName = "datasets_id_seq", allocationSize = 1)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @JsonIgnore
    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "file_name", nullable = false, unique = true)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "upload_date", nullable = false)
    private ZonedDateTime uploadDate;

    @ManyToOne
    @JoinColumn(name = "accessibility_id", nullable = false)
    private DatasetAccessibility accessibility;

    // Main category (required)

    @ManyToOne(optional = false)
    @JsonIgnore
    @JoinColumn(name = "category_id")
    private Category category;


    @Column(name = "description")
    private String description;

    @JsonManagedReference
    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DatasetConfiguration> datasetConfigurations;

    // New: Explicit relation to DatasetShare (Sharing info)
    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DatasetShare> datasetShares = new HashSet<>();
}
