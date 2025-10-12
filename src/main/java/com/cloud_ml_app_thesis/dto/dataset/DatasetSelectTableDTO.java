package com.cloud_ml_app_thesis.dto.dataset;

import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DatasetSelectTableDTO {
    private Integer id;
    private String originalFileName;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private ZonedDateTime uploadDate;
    private DatasetAccessibilityEnum status;
    private String description;
    private long completeTrainingCount;
    private long failedTrainingCount;
    private String ownerUsername;
}
