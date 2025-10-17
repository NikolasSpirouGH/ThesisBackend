package com.cloud_ml_app_thesis.dto.request.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModelSearchRequest {
    // Simple search: keyword that matches any metadata
    private String keyword;

    // Advanced search fields
    private String name;
    private String description;
    private Set<String> keywords;
    private Set<Integer> categoryIds;
    private String accessibility; // PUBLIC, PRIVATE
    private String modelType; // CLASSIFICATION, REGRESSION, CLUSTERING

    // Date ranges
    private ZonedDateTime trainingDateFrom;
    private ZonedDateTime trainingDateTo;
    private ZonedDateTime creationDateFrom;
    private ZonedDateTime creationDateTo;

    // Search mode: AND or OR for advanced search
    private String searchMode; // "AND" or "OR", default "AND"
}
