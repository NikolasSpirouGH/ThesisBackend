package com.cloud_ml_app_thesis.entity;

import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    // Self-referencing Many-to-Many for hierarchical categories
    @ManyToMany
    @JsonIgnore
    @JoinTable(
            name = "category_hierarchy",
            joinColumns = @JoinColumn(name = "child_category_id"),
            inverseJoinColumns = @JoinColumn(name = "parent_category_id")
    )
    private Set<Category> parentCategories;

    @JsonIgnore
    @ManyToMany(mappedBy = "parentCategories")
    private Set<Category> childCategories;

    @JsonIgnore
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Dataset> datasets = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Model> models = new HashSet<>();

    @Column(nullable = false)
    private boolean deleted = false;

}
