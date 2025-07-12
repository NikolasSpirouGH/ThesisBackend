package com.cloud_ml_app_thesis.dto.request.algorithm_configuration;

import com.cloud_ml_app_thesis.entity.Algorithm;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class AlgorithmConfigurationCreateRequest {

    //TODO Agree to set the default options in case of null or send them from the frontend so we have to add @NotBlank
    @Size(max = 250, message = "Default options cannot exceed 250 characters")
    private String options;
}
