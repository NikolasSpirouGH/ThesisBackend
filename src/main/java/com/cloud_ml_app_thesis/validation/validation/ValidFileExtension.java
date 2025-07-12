package com.cloud_ml_app_thesis.validation.validation;

import com.cloud_ml_app_thesis.validation.validator.FileExtensionValidator;
import jakarta.validation.Constraint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FileExtensionValidator.class)
public @interface ValidFileExtension {
    String[] extensions();
    String message() default "Invalid file type. Only .csv and .arff file types are allowed.";
    Class<?>[] groups() default {};
}
