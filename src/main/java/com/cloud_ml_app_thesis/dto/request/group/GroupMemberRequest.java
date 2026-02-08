package com.cloud_ml_app_thesis.dto.request.group;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class GroupMemberRequest {
    @NotEmpty(message = "At least one username is required")
    private Set<String> usernames;
}
