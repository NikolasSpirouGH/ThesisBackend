package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.Algorithm;
import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlgorithmConfigurationRepository extends JpaRepository<AlgorithmConfiguration, Integer> {
    @Query("SELECT ac FROM AlgorithmConfiguration ac " +
           "JOIN FETCH ac.algorithm a " +
           "JOIN FETCH a.type " +
           "WHERE ac.algorithm = :algorithm AND ac.options = :options")
    List<AlgorithmConfiguration> findByAlgorithmAndOptions(@Param("algorithm") Algorithm algorithm,
                                                             @Param("options") String options);
}
