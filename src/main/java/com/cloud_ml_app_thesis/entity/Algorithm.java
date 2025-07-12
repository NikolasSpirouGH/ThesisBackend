package com.cloud_ml_app_thesis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@Entity
@Table(name = "algorithms", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Algorithm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(length = 5000)
    private String description;

    @Column(length = 5000)
    private String options;

    @Column(length = 5000)
    private String optionsDescription;

    @Column(length = 5000)
    private String defaultOptions;

    @Column(name = "class_name")
    private String className;

}
