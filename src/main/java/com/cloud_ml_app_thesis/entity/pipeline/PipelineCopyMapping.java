package com.cloud_ml_app_thesis.entity.pipeline;

import com.cloud_ml_app_thesis.enumeration.PipelineEntityTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.PipelineCopyMappingStatusEnum;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pipeline_copy_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineCopyMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_copy_id", nullable = false)
    private PipelineCopy pipelineCopy;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private PipelineEntityTypeEnum entityType;

    @Column(name = "source_entity_id", nullable = false)
    private Integer sourceEntityId;

    @Column(name = "target_entity_id")
    private Integer targetEntityId;

    @Column(name = "minio_source_key", length = 500)
    private String minioSourceKey;

    @Column(name = "minio_target_key", length = 500)
    private String minioTargetKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PipelineCopyMappingStatusEnum status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = PipelineCopyMappingStatusEnum.PENDING;
        }
    }
}
