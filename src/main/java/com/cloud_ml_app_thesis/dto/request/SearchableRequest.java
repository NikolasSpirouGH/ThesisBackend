package com.cloud_ml_app_thesis.dto.request;

import java.util.List;

public interface SearchableRequest {
    String getSimpleSearchInput();
    String getName();
    String getDescription();
    List<String> getKeywords();
    Object getAccessibility();
    String getCreatedAtFrom();
    String getCreatedAtTo();
}
