package com.cloud_ml_app_thesis.validation.validation;

import com.cloud_ml_app_thesis.validation.validator.TarballContentValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;


@Documented
@Constraint(validatedBy = TarballContentValidator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
public @interface ValidTarball {
    String message() default "Tarball must contain Dockerfile, train.py, predict.py and requirements.txt";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
