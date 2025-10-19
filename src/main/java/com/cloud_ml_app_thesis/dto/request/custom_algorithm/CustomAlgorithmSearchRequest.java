package com.cloud_ml_app_thesis.dto.request.custom_algorithm;

import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomAlgorithmSearchRequest {

    private String keyword;
    private String name;
    private String description;
    private List<String> keywords;
    private AlgorithmAccessibiltyEnum accessibility;
    private String version;
    private String createdAtFrom;
    private String createdAtTo;
    private SearchMode searchMode = SearchMode.AND;

    public enum SearchMode {
        AND, OR
    }
}
