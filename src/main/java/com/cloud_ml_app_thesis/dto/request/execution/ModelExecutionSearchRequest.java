package com.cloud_ml_app_thesis.dto.request.execution;

import lombok.Data;

@Data
public class ModelExecutionSearchRequest {
    private String executedAtFrom;
    private String executedAtTo;
}
