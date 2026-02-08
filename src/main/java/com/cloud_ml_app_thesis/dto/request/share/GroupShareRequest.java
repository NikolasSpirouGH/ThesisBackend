package com.cloud_ml_app_thesis.dto.request.share;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupShareRequest {
    @NotNull(message = "Group ID is required")
    private Integer groupId;

    private String comment;
}
