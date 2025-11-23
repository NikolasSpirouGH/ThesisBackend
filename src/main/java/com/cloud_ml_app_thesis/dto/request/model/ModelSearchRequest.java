package com.cloud_ml_app_thesis.dto.request.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModelSearchRequest {
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
