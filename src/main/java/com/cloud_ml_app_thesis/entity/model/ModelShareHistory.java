package com.cloud_ml_app_thesis.entity.model;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.action.ModelShareActionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Entity
@Table(name = "model_share_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModelShareHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "model_id")
    private Model model;

    @ManyToOne(optional = true)
    @JoinColumn(name = "shared_with_user_id")
    private User sharedWithUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "action_performed_by")
    private User performedBy;

    @ManyToOne(optional = false)
    @JoinColumn(name = "action", nullable = false)
    private ModelShareActionType action; // SHARE, REVOKE, etc.

    @Column(name = "action_time", nullable = false)
    private ZonedDateTime actionTime;

    private String comment; // optional note
}
