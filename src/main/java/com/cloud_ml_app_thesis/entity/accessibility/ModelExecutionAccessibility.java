package com.cloud_ml_app_thesis.entity.accessibility;

import com.cloud_ml_app_thesis.enumeration.accessibility.ModelExecutionAccessibilityEnum;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name= "CONST_MODEL_EXECUTION_ACCESSIBILITIES")
public class ModelExecutionAccessibility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ModelExecutionAccessibilityEnum name;

    @Column(nullable = false)
    private String description;
}