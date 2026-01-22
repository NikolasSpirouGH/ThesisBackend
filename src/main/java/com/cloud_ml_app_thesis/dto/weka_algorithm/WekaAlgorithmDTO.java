package com.cloud_ml_app_thesis.dto.weka_algorithm;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WekaAlgorithmDTO {
    private Integer id;
    private String description;
    private String name;
    private String type;  // CLASSIFICATION, REGRESSION, CLUSTERING
    private List<WekaAlgorithmOptionDTO> options;
    private String defaultOptionsString;
}
