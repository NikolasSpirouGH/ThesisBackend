package com.cloud_ml_app_thesis.entity.pipeline;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.action.PipelineCopyActionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "pipeline_copy_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineCopyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_copy_id", nullable = false)
    private PipelineCopy pipelineCopy;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "action_type", nullable = false)
    private PipelineCopyActionType actionType;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "action_by_user_id", nullable = false)
    private User actionByUser;

    @Column(name = "action_at", nullable = false)
    private ZonedDateTime actionAt;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @PrePersist
    protected void onCreate() {
        if (actionAt == null) {
            actionAt = ZonedDateTime.now();
        }
    }
}
