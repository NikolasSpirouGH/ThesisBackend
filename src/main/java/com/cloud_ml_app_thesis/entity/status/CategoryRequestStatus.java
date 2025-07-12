package com.cloud_ml_app_thesis.entity.status;

import com.cloud_ml_app_thesis.enumeration.status.CategoryRequestStatusEnum;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name= "CONST_CATEGORY_REQUEST_STATUSES")
public class CategoryRequestStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "name")
    @Enumerated(EnumType.STRING)
    private CategoryRequestStatusEnum name;
    @Column(name = "description", length = 1000)
    private String description;
}
