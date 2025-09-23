package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.CustomAlgorithm;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.accessibility.CustomAlgorithmAccessibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomAlgorithmRepository extends JpaRepository<CustomAlgorithm, Integer> {

    List<CustomAlgorithm> findByOwner(User owner);

    List<CustomAlgorithm> findByAccessibility(CustomAlgorithmAccessibility accessibility);

    @Query("SELECT ca FROM CustomAlgorithm ca WHERE ca.owner = :user OR ca.accessibility.name = 'PUBLIC'")
    List<CustomAlgorithm> findByOwnerOrPublic(@Param("user") User user);
}
