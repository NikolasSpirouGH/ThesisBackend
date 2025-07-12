package com.cloud_ml_app_thesis.repository.model;

import com.cloud_ml_app_thesis.entity.model.ModelShareHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelShareHistoryRepository extends JpaRepository<ModelShareHistory, Integer> {
}
