package com.cloud_ml_app_thesis.dto.request.dataset;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DatasetSearchRequest {
    private String name;
    private String ownerUsername;
    private Integer categoryId; // Filtering datasets by category
    private Boolean includeChildCategories; // If true, search child categories as well
    private Boolean isPublic; // True = Public, False = Private, Null = Both
    private String contentType;
    private String uploadDateFrom;
    private String uploadDateTo;
}

