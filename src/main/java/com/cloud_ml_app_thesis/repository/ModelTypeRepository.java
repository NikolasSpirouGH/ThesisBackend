package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.ModelType;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelTypeRepository extends JpaRepository<ModelType, Integer> {
    Optional<ModelType> findByName(ModelTypeEnum modelTypeEnum);
}
