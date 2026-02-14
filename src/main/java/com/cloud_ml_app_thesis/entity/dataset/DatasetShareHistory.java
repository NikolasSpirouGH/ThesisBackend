package com.cloud_ml_app_thesis.entity.dataset;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.action.DatasetShareActionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DatasetShareHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @ManyToOne(optional = false)
    private Dataset dataset;

    @ManyToOne(optional = true)
    private User targetUser;

    @ManyToOne(optional = false)
    private User actionByUser;

    private ZonedDateTime actionAt;

    @ManyToOne
    @JoinColumn(name = "action_type")
    private DatasetShareActionType actionType; // SHARED, REMOVED, DECLINED

    private String comment;
}