package com.cloud_ml_app_thesis.entity.dataset;

import com.cloud_ml_app_thesis.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dataset_copies")
public class DatasetCopy {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "original_dataset_id", nullable = false)
    private Dataset originalDataset;

    @ManyToOne
    @JoinColumn(name = "copied_by_user_id", nullable = false)
    private User copiedBy;

    @ManyToOne
    @JoinColumn(name = "copy_operated_by_user_id", nullable = false)
    private User copyOperatedBy;

    private ZonedDateTime copyDate;
}
