package com.cloud_ml_app_thesis.dto.pipeline;

import com.cloud_ml_app_thesis.enumeration.status.PipelineCopyStatusEnum;
import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.Map;

@Data
@Builder
public class PipelineCopyResponse {
    private Integer pipelineCopyId;
    private Integer sourceTrainingId;
    private Integer targetTrainingId;
    private String copiedByUsername;
    private String copyForUsername;
    private ZonedDateTime copyDate;
    private PipelineCopyStatusEnum status;
    private String errorMessage;

    private Map<String, EntityMapping> mappings;

    @Data
    @Builder
    public static class EntityMapping {
        private Integer sourceId;
        private Integer targetId;
        private String minioSourceKey;
        private String minioTargetKey;
        private String status;
    }
}
