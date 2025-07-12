package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlgorithmConfigurationRepository extends JpaRepository<AlgorithmConfiguration, Integer> {
}
