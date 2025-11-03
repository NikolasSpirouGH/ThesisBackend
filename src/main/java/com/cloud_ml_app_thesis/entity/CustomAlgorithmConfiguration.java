package com.cloud_ml_app_thesis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name="custom_algorithm_configurations")
public class CustomAlgorithmConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "algorithm_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CustomAlgorithm algorithm;

    @OneToMany(mappedBy = "configuration", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlgorithmParameter> parameters = new ArrayList<>();

}
