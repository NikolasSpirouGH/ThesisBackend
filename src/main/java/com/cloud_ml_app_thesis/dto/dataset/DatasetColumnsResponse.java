package com.cloud_ml_app_thesis.dto.dataset;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatasetColumnsResponse {
    private List<DatasetColumnDTO> columns;
    private Integer totalRows;
    private Integer totalColumns;
}
