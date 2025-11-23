package com.cloud_ml_app_thesis.repository.model;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.ModelExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelExecutionRepository  extends JpaRepository<ModelExecution, Integer> {
    Optional<ModelExecution> findByModelIdAndDatasetId(Integer modelId, Integer datasetId);

    int countByModelId(Integer id);

    @Query("SELECT me FROM ModelExecution me " +
           "LEFT JOIN FETCH me.model m " +
           "LEFT JOIN FETCH me.status s " +
           "LEFT JOIN FETCH me.dataset d " +
           "LEFT JOIN FETCH me.accessibility a " +
           "WHERE me.executedByUser = :user " +
           "ORDER BY me.executedAt DESC")
    List<ModelExecution> findByExecutedByUserWithDetails(@Param("user") User user);

    @Query("SELECT me FROM ModelExecution me " +
           "LEFT JOIN FETCH me.model m " +
           "LEFT JOIN FETCH me.status s " +
           "LEFT JOIN FETCH me.dataset d " +
           "LEFT JOIN FETCH me.accessibility a " +
           "ORDER BY me.executedAt DESC")
    List<ModelExecution> findAllWithDetails();

    @Query("SELECT DISTINCT me FROM ModelExecution me " +
           "LEFT JOIN FETCH me.model m " +
           "LEFT JOIN FETCH me.status s " +
           "LEFT JOIN FETCH me.dataset d " +
           "LEFT JOIN FETCH me.accessibility a " +
           "WHERE (me.executedByUser.id = :userId " +
           "   OR a.name = com.cloud_ml_app_thesis.enumeration.accessibility.ModelExecutionAccessibilityEnum.PUBLIC) " +
           "ORDER BY me.executedAt DESC")
    List<ModelExecution> findAccessibleToUser(@Param("userId") java.util.UUID userId);
}
