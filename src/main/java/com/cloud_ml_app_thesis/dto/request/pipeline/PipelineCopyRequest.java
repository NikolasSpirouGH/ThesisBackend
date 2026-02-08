package com.cloud_ml_app_thesis.dto.request.pipeline;

import lombok.Data;

@Data
public class PipelineCopyRequest {
    private String targetUsername;
    private Integer targetGroupId;
    private String comment;

    public boolean isGroupCopy() {
        return targetGroupId != null;
    }
}
