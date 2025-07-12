package com.cloud_ml_app_thesis.entity.accessibility;

import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name= "CONST_MODEL_ACCESSIBILITES")
public class ModelAccessibility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ModelAccessibilityEnum name;

    @Column(nullable = false)
    private String description;
}
