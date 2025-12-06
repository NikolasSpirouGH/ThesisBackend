package com.cloud_ml_app_thesis.dto.dataset;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DatasetColumnDTO {
    private Integer index;        // 1-based index (Weka uses 1-based)
    private String name;           // Column name
    private String type;           // "numeric", "nominal", "string", "date"
    private Integer distinctValues; // For nominal attributes
}
