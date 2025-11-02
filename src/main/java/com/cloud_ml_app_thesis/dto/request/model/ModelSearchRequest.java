package com.cloud_ml_app_thesis.dto.request.model;

import com.cloud_ml_app_thesis.dto.request.SearchableRequest;
import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmSearchRequest;
import com.cloud_ml_app_thesis.validation.validation.ValidSearchRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ValidSearchRequest
public class ModelSearchRequest implements SearchableRequest {
    // Simple search: keyword that matches any metadata
    private String simpleSearchInput;

    // Advanced search fields
    private String name;
    private String description;
    private List<String> keywords;
    private String accessibility; // PUBLIC, PRIVATE
    private String modelType; // CLASSIFICATION, REGRESSION, CLUSTERING
    private String category;
    private String createdAtFrom;
    private String createdAtTo;

    private SearchMode searchMode = SearchMode.OR;

    public enum SearchMode {
        AND, OR
    }
}
