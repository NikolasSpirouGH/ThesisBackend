package com.cloud_ml_app_thesis.entity.model;

import com.cloud_ml_app_thesis.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Entity
@Table(name = "model_shares",
        uniqueConstraints = @UniqueConstraint(columnNames = {"model_id", "shared_with_user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModelShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "model_id")
    private Model model;

    @ManyToOne(optional = false)
    @JoinColumn(name = "shared_with_user_id")
    private User sharedWithUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "shared_by_user_id")
    private User sharedByUser;

    @Column(nullable = false)
    private ZonedDateTime sharedAt;

    private String comment; // Optional description

}
