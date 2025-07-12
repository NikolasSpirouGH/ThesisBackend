package com.cloud_ml_app_thesis.entity.action;

import com.cloud_ml_app_thesis.enumeration.action.DatasetShareActionTypeEnum;
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
@Table(name = "CONST_DATASET_SHARE_ACTION_TYPES")
public class DatasetShareActionType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DatasetShareActionTypeEnum name; // SHARED, REMOVED, DECLINED

    @Column(nullable = false)
    private String description;
}