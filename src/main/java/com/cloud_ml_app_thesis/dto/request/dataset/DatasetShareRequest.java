package com.cloud_ml_app_thesis.dto.request.dataset;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class DatasetShareRequest {
    @NotNull(message = "Please provide the usernames of the users you want to share the dataset with")
    private Set<@NotBlank(message = "The usernames you provide must be a valid string") String> usernames;
    private String comment;
}
