package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByName(UserRoleEnum name);
}
