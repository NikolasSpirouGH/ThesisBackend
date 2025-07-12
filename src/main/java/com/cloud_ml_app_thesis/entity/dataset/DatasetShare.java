package com.cloud_ml_app_thesis.entity.dataset;

import com.cloud_ml_app_thesis.entity.User;
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
@Table(name = "dataset_shares",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"dataset_id", "shared_with_user_id"})
        })
public class DatasetShare {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "dataset_id")
    private Dataset dataset;

    @ManyToOne(optional = false)
    @JoinColumn(name = "shared_with_user_id")
    private User sharedWithUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "shared_by_user_id")
    private User sharedByUser;

    @Column(nullable = false)
    private ZonedDateTime sharedAt;

    private String comment;
}

