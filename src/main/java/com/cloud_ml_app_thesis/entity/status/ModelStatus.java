package com.cloud_ml_app_thesis.entity.status;

import com.cloud_ml_app_thesis.enumeration.status.ModelStatusEnum;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name= "CONST_MODEL_STATUSES")
public class ModelStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "name")
    @Enumerated(EnumType.STRING)
    private ModelStatusEnum name;
    @Column(name = "description", length = 1000)
    private String description;
}
