package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlgorithmTypeRepository extends JpaRepository<AlgorithmType, Integer> {


    Optional<AlgorithmType> findByName(AlgorithmTypeEnum typeEnum);
}
