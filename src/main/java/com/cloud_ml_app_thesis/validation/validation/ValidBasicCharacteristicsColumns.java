package com.cloud_ml_app_thesis.validation.validation;

import com.cloud_ml_app_thesis.validation.validator.BasicCharacteristicsColumnsValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = BasicCharacteristicsColumnsValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidBasicCharacteristicsColumns {
    String message() default "Basic Attributes Columns must be formatted as numbers greater than zero, separated by commas";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
