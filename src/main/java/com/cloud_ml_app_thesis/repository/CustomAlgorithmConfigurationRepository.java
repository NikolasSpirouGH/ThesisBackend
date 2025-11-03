package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.CustomAlgorithm;
import com.cloud_ml_app_thesis.entity.CustomAlgorithmConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomAlgorithmConfigurationRepository extends JpaRepository<CustomAlgorithmConfiguration, Integer> {
    List<CustomAlgorithmConfiguration> findByAlgorithm(CustomAlgorithm algorithm);
}
