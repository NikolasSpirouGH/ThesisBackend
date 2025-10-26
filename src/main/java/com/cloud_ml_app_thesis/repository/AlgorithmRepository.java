package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.Algorithm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlgorithmRepository extends JpaRepository<Algorithm, Integer> {
    Algorithm findByName(String name);

    boolean existsByName(String name);

    boolean existsByClassName(String className);
}
