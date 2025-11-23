package com.cloud_ml_app_thesis.dto.request.dataset;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DatasetSearchPayload extends DatasetSearchRequest {
    private int page = 0;
    private int size = 10;
    private String sortBy = "uploadDate";
    private String sortDirection = "ASC";
}
