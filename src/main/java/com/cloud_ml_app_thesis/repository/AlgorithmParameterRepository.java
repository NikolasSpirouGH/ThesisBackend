package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.AlgorithmParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlgorithmParameterRepository extends JpaRepository<AlgorithmParameter, Integer> {
}
