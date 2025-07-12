package com.cloud_ml_app_thesis.entity.action;

import com.cloud_ml_app_thesis.enumeration.action.DatasetShareActionTypeEnum;
import com.cloud_ml_app_thesis.enumeration.action.ModelShareActionTypeEnum;
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
@Table(name = "CONST_MODEL_SHARE_ACTION_TYPES")
public class ModelShareActionType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ModelShareActionTypeEnum name;

    @Column(nullable = false)
    private String description;
}