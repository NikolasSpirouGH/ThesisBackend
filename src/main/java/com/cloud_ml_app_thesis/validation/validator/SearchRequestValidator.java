package com.cloud_ml_app_thesis.validation.validator;

import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmSearchRequest;
import com.cloud_ml_app_thesis.validation.validation.ValidSearchRequest;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SearchRequestValidator implements ConstraintValidator<ValidSearchRequest, CustomAlgorithmSearchRequest> {

    @Override
    public boolean isValid(CustomAlgorithmSearchRequest request, ConstraintValidatorContext context) {
        if(request == null) {
            return true;
        }

        // If searchInput is provided, no advanced fields should be present
        if(StringUtils.isNotBlank(request.getSimpleSearchInput()) && !request.getSimpleSearchInput().equalsIgnoreCase("string")) {
            boolean hasAdvancedFields = StringUtils.isNotBlank(request.getName())
                    || StringUtils.isNotBlank(request.getDescription())
                    || request.getAccessibility() != null
                    || (request.getKeywords() != null && !request.getKeywords().isEmpty())
                    || StringUtils.isNotBlank(request.getCreatedAtFrom())
                    || StringUtils.isNotBlank(request.getCreatedAtTo());

            return !hasAdvancedFields;
        }

        return true;
    }
}