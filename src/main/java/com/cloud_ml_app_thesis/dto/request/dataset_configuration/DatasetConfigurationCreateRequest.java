package com.cloud_ml_app_thesis.dto.request.dataset_configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
public class DatasetConfigurationCreateRequest {
    private Integer id;
    private String basicAttributesColumns;
    private String targetClassColumn;
}
