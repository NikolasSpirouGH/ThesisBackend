package com.cloud_ml_app_thesis.entity.model;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.group.Group;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "model_group_shares",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"model_id", "group_id"})
        })
public class ModelGroupShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "model_id")
    private Model model;

    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne(optional = false)
    @JoinColumn(name = "shared_by_user_id")
    private User sharedByUser;

    @Column(nullable = false)
    private ZonedDateTime sharedAt;

    private String comment;
}
