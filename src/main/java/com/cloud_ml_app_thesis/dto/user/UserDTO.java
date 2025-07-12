package com.cloud_ml_app_thesis.dto.user;

import lombok.*;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {

    private UUID id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Integer age;
    private String profession;
    private String country;
    private String status;
    private Set<String> roles;
}