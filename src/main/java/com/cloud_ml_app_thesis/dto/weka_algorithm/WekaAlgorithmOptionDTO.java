package com.cloud_ml_app_thesis.dto.weka_algorithm;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WekaAlgorithmOptionDTO {
    private String flag;           // e.g., "C", "M", "N"
    private String description;    // e.g., "Confidence factor for pruning"
    private String type;           // e.g., "numeric", "boolean", "string"
    private String defaultValue;   // e.g., "0.25", "2", "true"
}
