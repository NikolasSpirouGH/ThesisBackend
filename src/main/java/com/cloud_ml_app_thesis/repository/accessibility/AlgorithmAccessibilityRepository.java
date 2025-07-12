package com.cloud_ml_app_thesis.repository.accessibility;

import com.cloud_ml_app_thesis.entity.accessibility.CustomAlgorithmAccessibility;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface AlgorithmAccessibilityRepository extends JpaRepository<CustomAlgorithmAccessibility, Integer> {
    Optional<CustomAlgorithmAccessibility> findByName(@NotNull AlgorithmAccessibiltyEnum accessibility);
}
