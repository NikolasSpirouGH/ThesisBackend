package com.cloud_ml_app_thesis.repository.accessibility;

import com.cloud_ml_app_thesis.entity.accessibility.DatasetAccessibility;
import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DatasetAccessibilityRepository extends JpaRepository<DatasetAccessibility, Integer> {
    Optional<DatasetAccessibility> findByName(DatasetAccessibilityEnum name);

}
