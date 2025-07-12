package com.cloud_ml_app_thesis.dto.request.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelShareRequest {
    private Set<String> usernames; // can be null/empty for revoke-all
    private String comment;
}