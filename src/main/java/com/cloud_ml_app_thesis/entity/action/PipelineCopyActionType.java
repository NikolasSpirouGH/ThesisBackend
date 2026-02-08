package com.cloud_ml_app_thesis.entity.action;

import com.cloud_ml_app_thesis.enumeration.action.PipelineCopyActionTypeEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "CONST_PIPELINE_COPY_ACTION_TYPES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PipelineCopyActionType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
    private PipelineCopyActionTypeEnum name;

    @Column(nullable = false)
    private String description;
}
