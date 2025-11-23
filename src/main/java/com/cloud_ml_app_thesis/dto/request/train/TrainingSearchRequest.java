package com.cloud_ml_app_thesis.dto.request.train;

import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TrainingSearchRequest {
    private LocalDate fromDate;
    private Integer algorithmId;
    private ModelTypeEnum type;
}
