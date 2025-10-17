package com.cloud_ml_app_thesis.dto.request.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModelUpdateRequest {
    private String name;
    private Set<@Size(max = 25) String> keywords;
    @Size(max = 500)
    private String description;
    @Size(max = 500)
    private String dataDescription;
    private Integer categoryId;

    @JsonProperty("isPublic")
    private boolean isPublic;
}
