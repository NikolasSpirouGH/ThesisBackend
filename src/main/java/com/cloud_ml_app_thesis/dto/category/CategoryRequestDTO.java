package com.cloud_ml_app_thesis.dto.category;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class CategoryRequestDTO {
    private Integer id;
    private String name;
    private String description;
    private String requestedByUsername;
    private String processedByUsername;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private Set<Integer> parentCategoryIds;
}