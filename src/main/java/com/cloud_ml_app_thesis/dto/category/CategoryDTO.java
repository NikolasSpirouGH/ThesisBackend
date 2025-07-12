package com.cloud_ml_app_thesis.dto.category;

import lombok.Data;

import java.util.Set;

@Data
public class CategoryDTO {
    private Integer id;
    private String name;
    private String description;
    private String createdByUsername;
    private Set<Integer> parentCategoryIds;
}