package com.cloud_ml_app_thesis.entity;

import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name= "CONST_MODEL_TYPES")
public class ModelType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "name")
    @Enumerated(EnumType.STRING)
    private ModelTypeEnum name;

    public ModelType(ModelTypeEnum name) {
        this.name = name;
    }

}
