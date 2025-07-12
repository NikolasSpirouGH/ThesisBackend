package com.cloud_ml_app_thesis.repository.accessibility;

import com.cloud_ml_app_thesis.entity.accessibility.ModelAccessibility;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelAccessibilityRepository extends JpaRepository<ModelAccessibility, Integer> {
    Optional<ModelAccessibility> findByName(ModelAccessibilityEnum name);
}

