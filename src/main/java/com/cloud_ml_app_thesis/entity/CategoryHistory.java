package com.cloud_ml_app_thesis.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "category_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private Category category; // The category that was edited

    @ManyToOne
    @JoinColumn(name = "edited_by", nullable = false)
    private User editedBy; // Who edited it

    @Column(nullable = false)
    private LocalDateTime editedAt; // When it was edited

    @Column(length = 5000)
    private String oldValues; // Store previous values in JSON format

    @Column(length = 5000)
    private String newValues; // Store updated values in JSON format

    @Column(length = 100)
    private String comments;

    @Column
    private boolean initial = false;
}
