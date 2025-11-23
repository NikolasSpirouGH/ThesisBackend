package com.cloud_ml_app_thesis.dto.request.category;

import lombok.Data;

import java.util.List;

@Data
public class CategorySearchRequest{
    private String simpleSearchInput;
    private String name;
    private String description;
    private List<String> keywords;
    private String createdAtFrom;
    private String createdAtTo;
    private String createdByUsername;
    private Integer parentCategoryId;
    private Boolean includeDeleted = false;
    private SearchMode searchMode = SearchMode.OR;

    public enum SearchMode {
        AND, OR
    }
}
