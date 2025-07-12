package com.cloud_ml_app_thesis.repository.status;

import com.cloud_ml_app_thesis.entity.status.ModelStatus;
import com.cloud_ml_app_thesis.enumeration.status.ModelStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelStatusRepository extends JpaRepository<ModelStatus, Integer> {

    Optional<ModelStatus> findByName(ModelStatusEnum name);
}
