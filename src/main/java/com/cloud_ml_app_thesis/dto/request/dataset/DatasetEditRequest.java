package com.cloud_ml_app_thesis.dto.request.dataset;

import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

//TODO DEFINE THE FIELDS FOR EDIT
public class DatasetEditRequest {
    @Size(min = 36, max = 36, message = "User id must be 36 characters")
    private String userOwnerId;

    @Size(max = 50, message = "Original file name  must be at most 50 characters")
    private String originalFileName;

    @Size(max = 50, message = "File name must be at most 50 characters")
    private String fileName;
    @Size(max = 50, message = "File path must be at most 50 characters")
    private String filePath;

    @NotBlank(message = "To create dataset you must provide accessibility type")
    private DatasetAccessibilityEnum accessibility;

    private Set<UUID> sharedUserIds;

    @Size(max = 5000, message = "Description cannot exceed 500 characters")
    private String description;

    @Positive(message = "Id cannot be negative")
    private Integer categoryId;
}
