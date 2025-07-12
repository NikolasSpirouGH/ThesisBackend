package com.cloud_ml_app_thesis.repository.model;

import com.cloud_ml_app_thesis.entity.model.ModelExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelExecutionRepository  extends JpaRepository<ModelExecution, Integer> {
    Optional<ModelExecution> findByModelIdAndDatasetId(Integer modelId, Integer datasetId);

}
