package com.cloud_ml_app_thesis.dto.request.custom_algorithm;

import com.cloud_ml_app_thesis.dto.request.SearchableRequest;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.validation.validation.ValidSearchRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ValidSearchRequest
public class CustomAlgorithmSearchRequest implements SearchableRequest {

    private String simpleSearchInput;
    private String name;
    private String description;
    private List<String> keywords;
    private AlgorithmAccessibiltyEnum accessibility;
    private String createdAtFrom;
    private String createdAtTo;
    private SearchMode searchMode = SearchMode.OR;

    public enum SearchMode {
        AND, OR
    }
}
