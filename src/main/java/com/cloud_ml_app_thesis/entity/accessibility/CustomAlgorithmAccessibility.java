package com.cloud_ml_app_thesis.entity.accessibility;

import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Table(name="CONST_ALGORITHM_ACCESSIBILITIES")
public class CustomAlgorithmAccessibility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    @Enumerated(EnumType.STRING)
    private AlgorithmAccessibiltyEnum name;

    @Column
    private String description;
}
