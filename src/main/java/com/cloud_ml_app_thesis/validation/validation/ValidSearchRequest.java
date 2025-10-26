package com.cloud_ml_app_thesis.validation.validation;

import com.cloud_ml_app_thesis.validation.validator.SearchRequestValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = SearchRequestValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSearchRequest {
    String message() default "searchInput (simple search) cannot be combined with advanced search fields";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}