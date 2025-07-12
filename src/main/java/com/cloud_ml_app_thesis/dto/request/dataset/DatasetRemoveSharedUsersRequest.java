package com.cloud_ml_app_thesis.dto.request.dataset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class DatasetRemoveSharedUsersRequest {
    private Set<@NotBlank(message = "The usernames you provide must be a valid string") String> usernames;
    @Size(max = 100)
    private String comments;
}
