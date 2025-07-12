package com.cloud_ml_app_thesis.validation.validation;

import com.cloud_ml_app_thesis.validation.validator.ImageSourceValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ImageSourceValidator.class)
@Documented
public @interface ValidImageSource {
    String message() default "Provide either a DockerHub URL or a Docker TAR file, not both or none.";

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
