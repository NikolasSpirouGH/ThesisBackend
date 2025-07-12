package com.cloud_ml_app_thesis.dto.request.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class CategoryCreateRequest {

    private Integer id;

    @NotBlank
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotBlank
    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    //If null it is a parent category
    private Set<Integer> parentCategoryIds;

    @NotNull
    private boolean force;
}
