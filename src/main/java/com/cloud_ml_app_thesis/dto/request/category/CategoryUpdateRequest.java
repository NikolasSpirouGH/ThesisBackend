package com.cloud_ml_app_thesis.dto.request.category;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Setter
@Getter
public class CategoryUpdateRequest {

    @Size(max = 255, message = "Name must be at most 100 characters")
    private String name;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    private Set<@Valid @Positive(message = "Negative id cannot exist") Integer> newParentCategoryIds;

    private Set<@Valid @Positive(message = "Negative id cannot exist") Integer> parentCategoryIdsToRemove;

}
