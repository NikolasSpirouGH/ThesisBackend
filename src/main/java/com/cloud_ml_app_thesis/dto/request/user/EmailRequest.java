package com.cloud_ml_app_thesis.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class EmailRequest {

    @NotBlank
    @Email
    String email;
}
