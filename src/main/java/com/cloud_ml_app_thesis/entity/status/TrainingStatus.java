package com.cloud_ml_app_thesis.entity.status;

import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name= "CONST_TRAINING_STATUSES")
public class TrainingStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    @Enumerated(EnumType.STRING)
    private TrainingStatusEnum name;

    @Column(name = "description", length = 1000)
    private String description;
}
