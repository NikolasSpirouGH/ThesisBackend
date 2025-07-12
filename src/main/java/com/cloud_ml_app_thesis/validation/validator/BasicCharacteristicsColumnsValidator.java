package com.cloud_ml_app_thesis.validation.validator;

import com.cloud_ml_app_thesis.validation.validation.ValidBasicCharacteristicsColumns;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BasicCharacteristicsColumnsValidator implements ConstraintValidator<ValidBasicCharacteristicsColumns, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // Not validating empty value here; other constraints take care of it
        }
        String[] numbers = value.split(",");
        for (String number : numbers) {
            try {
                if (Integer.parseInt(number.trim()) <= 0) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
