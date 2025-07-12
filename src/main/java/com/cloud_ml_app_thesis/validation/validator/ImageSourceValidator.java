package com.cloud_ml_app_thesis.validation.validator;

import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmCreateRequest;
import com.cloud_ml_app_thesis.dto.request.training.CustomTrainRequest;
import com.cloud_ml_app_thesis.validation.validation.ValidImageSource;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ImageSourceValidator implements ConstraintValidator<ValidImageSource, CustomAlgorithmCreateRequest> {

    @Override
    public boolean isValid(CustomAlgorithmCreateRequest request, ConstraintValidatorContext context) {
        boolean hasTar = request.getDockerTarFile() != null && !request.getDockerTarFile().isEmpty();
        boolean hasUrl = request.getDockerHubUrl() != null && !request.getDockerHubUrl().isBlank();

        if (hasTar ^ hasUrl) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                "📦 Please provide either a DockerHub URL or a Docker TAR file — not both or neither!"
        ).addConstraintViolation();

        return false;
    }
}
