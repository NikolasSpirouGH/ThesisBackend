package com.cloud_ml_app_thesis.entity;

import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name= "CONST_ALGORITHM_TYPES")
public class AlgorithmType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    @Enumerated(EnumType.STRING)
    private AlgorithmTypeEnum name;

    public AlgorithmType(AlgorithmTypeEnum name) {
        this.name = name;
    }

}
