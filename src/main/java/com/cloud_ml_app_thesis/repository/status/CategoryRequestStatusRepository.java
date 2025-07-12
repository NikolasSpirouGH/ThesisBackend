package com.cloud_ml_app_thesis.repository.status;

import com.cloud_ml_app_thesis.entity.CategoryRequest;
import com.cloud_ml_app_thesis.entity.status.CategoryRequestStatus;
import com.cloud_ml_app_thesis.enumeration.status.CategoryRequestStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRequestStatusRepository extends JpaRepository<CategoryRequestStatus, Integer> {
    Optional<CategoryRequestStatus> findByName(CategoryRequestStatusEnum categoryRequestStatusEnum);
}
