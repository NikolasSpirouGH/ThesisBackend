package com.cloud_ml_app_thesis.repository.status;

import com.cloud_ml_app_thesis.entity.status.ModelExecutionStatus;
import com.cloud_ml_app_thesis.enumeration.status.ModelExecutionStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelExecutionStatusRepository extends JpaRepository<ModelExecutionStatus, Integer> {
    Optional<ModelExecutionStatus> findByName(ModelExecutionStatusEnum name);
}
