package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrainingRepository extends JpaRepository<Training, Integer> {
    Optional<List<Training>> findAllByOrderByStatusAsc();

    Optional<List<Training>> findAllByUserUsernameAndStatus(String username, TrainingStatusEnum status);
    Optional<List<Training>> findAllByUserUsernameOrderByFinishedDateDesc(String username);


Optional<List<Training>> findAllByUserIdAndStatus(UUID userId, TrainingStatusEnum status);
    Optional<List<Training>> findAllByUserId(UUID userId);

    Optional<Training> findByModel(Model model);

    long countByDatasetConfigurationDatasetId(Integer datasetId);
    long countByDatasetConfigurationDatasetIdAndStatus(Integer datasetId, TrainingStatusEnum status);

    long countByDatasetConfigurationIdAndStatus(Integer datasetConfigurationId, TrainingStatusEnum status);


    boolean existsByCustomAlgorithmConfiguration_Algorithm_IdAndDatasetConfiguration_Id(Integer id, Integer id1);
}
