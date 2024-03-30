package com.backend.mlapp.payload;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UpdateRequest {

    @Email
    private String email;

    private String firstname;

    private String lastname;

    private String country;

    private String profession;

    private Integer age;
}
