package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.enumeration.status.DatasetConfigurationStatusEnum;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetConfigurationRepository extends JpaRepository<DatasetConfiguration, Integer> {
    Optional<List<DatasetConfiguration>> findAllByDatasetUserUsername(String username);
    Optional<List<DatasetConfiguration>> findAllByDatasetUserUsernameAndStatus(String username, DatasetConfigurationStatusEnum status);
}
