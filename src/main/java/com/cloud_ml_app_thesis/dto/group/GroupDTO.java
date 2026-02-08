package com.cloud_ml_app_thesis.dto.group;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
public class GroupDTO {
    private Integer id;
    private String name;
    private String description;
    private String leaderUsername;
    private List<String> memberUsernames;
    private ZonedDateTime createdAt;
    private int memberCount;
}
