package com.cloud_ml_app_thesis.entity.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "keywords", uniqueConstraints = @UniqueConstraint(columnNames= "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Keyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "keywords")
    private Set<Model> models = new HashSet<>();

    public Keyword(String name){
        this.id = null;
        this.name = name;
        this.models = null;
    }
}
