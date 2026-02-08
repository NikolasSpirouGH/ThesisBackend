package com.cloud_ml_app_thesis.entity.pipeline;

import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.enumeration.status.PipelineCopyStatusEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pipeline_copies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "source_training_id", nullable = false)
    private Training sourceTraining;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_training_id")
    private Training targetTraining;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "copied_by_user_id", nullable = false)
    private User copiedByUser;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "copy_for_user_id", nullable = false)
    private User copyForUser;

    @Column(name = "copy_date", nullable = false)
    private ZonedDateTime copyDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PipelineCopyStatusEnum status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @OneToMany(mappedBy = "pipelineCopy", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PipelineCopyMapping> mappings = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (copyDate == null) {
            copyDate = ZonedDateTime.now();
        }
        if (status == null) {
            status = PipelineCopyStatusEnum.IN_PROGRESS;
        }
    }

    public void addMapping(PipelineCopyMapping mapping) {
        mappings.add(mapping);
        mapping.setPipelineCopy(this);
    }
}
