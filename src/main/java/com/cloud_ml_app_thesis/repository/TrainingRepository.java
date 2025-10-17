package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrainingRepository extends JpaRepository<Training, Integer> {
    Optional<Training> findByModel(Model model);

    @Query("SELECT COUNT(t) FROM Training t WHERE t.datasetConfiguration.dataset.id = :datasetId AND t.status.name = :status")
    long countByDatasetConfigurationDatasetIdAndStatus(@Param("datasetId") Integer datasetId, @Param("status") TrainingStatusEnum status);

    @Query("SELECT COUNT(t) FROM Training t WHERE t.datasetConfiguration.id = :datasetConfigurationId AND t.status.name = :status")
    long countByDatasetConfigurationIdAndStatus(@Param("datasetConfigurationId") Integer datasetConfigurationId, @Param("status") TrainingStatusEnum status);


    boolean existsByCustomAlgorithmConfiguration_Algorithm_IdAndDatasetConfiguration_Id(Integer id, Integer id1);

    List<Training> findAllByUser(User user);

    @Query("""
            SELECT training FROM Training training
            WHERE training.user = :user
              AND training.status.name = com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum.COMPLETED
            ORDER BY training.startedDate DESC
            """)
    List<Training> findCompletedTrainingsForUser(@Param("user") User user);

    @Query("""
            SELECT training FROM Training training
            WHERE training.status.name = com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum.COMPLETED
            ORDER BY training.startedDate DESC
            """)
    List<Training> findCompletedTrainings();

    @Query("""
    SELECT training FROM Training training
    WHERE training.user = :user AND training.startedDate >= :fromDate
    ORDER BY training.startedDate DESC
""")
    List<Training> findAllFromDate(@Param("user") User user, @Param("fromDate") ZonedDateTime fromDate);

    @Query("""
    SELECT training FROM Training training
    LEFT JOIN training.customAlgorithmConfiguration algorithm_conf
    WHERE training.user = :user AND algorithm_conf.algorithm.id = :algorithmId
    ORDER BY training.startedDate DESC
""")
    List<Training> findByCustomAlgorithm(@Param("user") User user, @Param("algorithmId") Integer algorithmId);

    @Query("""
    SELECT training FROM Training training
    LEFT JOIN training.algorithmConfiguration algorithm_conf
    WHERE training.user = :user AND algorithm_conf.algorithm.id = :algorithmId
    ORDER BY training.startedDate DESC
""")
    List<Training> findByPredefinedAlgorithm(@Param("user") User user, @Param("algorithmId") Integer algorithmId);

    @Query("""
    SELECT training FROM Training training
    LEFT JOIN training.customAlgorithmConfiguration algorithm_conf
    WHERE training.user = :user AND training.startedDate >= :fromDate AND algorithm_conf.algorithm.id = :algorithmId
    ORDER BY training.startedDate DESC
""")
    List<Training> findFromDateAndCustomAlgorithm(@Param("user") User user,
                                                  @Param("fromDate") ZonedDateTime fromDate,
                                                  @Param("algorithmId") Integer algorithmId);

    @Query("""
    SELECT training FROM Training training
    LEFT JOIN training.algorithmConfiguration algorithm_conf
    WHERE training.user = :user AND training.startedDate >= :fromDate AND algorithm_conf.algorithm.id = :algorithmId
    ORDER BY training.startedDate DESC
""")
    List<Training> findFromDateAndPredefinedAlgorithm(@Param("user") User user,
                                                      @Param("fromDate") ZonedDateTime fromDate,
                                                      @Param("algorithmId") Integer algorithmId);

    @Query("""
    SELECT training FROM Training training
    JOIN training.customAlgorithmConfiguration algorithm_conf
    WHERE training.user = :user
    ORDER BY training.startedDate DESC
""")
    List<Training> findAllCustom(@Param("user") User user);

    @Query("""
    SELECT training FROM Training training
    JOIN training.algorithmConfiguration algorithm_conf
    WHERE training.user = :user
    ORDER BY training.startedDate DESC
""")
    List<Training> findAllPredefined(@Param("user") User user);

    @Query("""
    SELECT training FROM Training training
    JOIN training.customAlgorithmConfiguration algorithm_conf
    WHERE training.user = :user AND training.startedDate >= :from AND training.algorithmConfiguration IS NULL
    ORDER BY training.startedDate DESC
""")
    List<Training> findAllFromDateAndCustom(@Param("user") User user, @Param("from") ZonedDateTime from);

    @Query("""
    SELECT training FROM Training training
    JOIN training.algorithmConfiguration algorithm_conf
    WHERE training.user = :user AND training.startedDate >= :from AND training.customAlgorithmConfiguration IS NULL
    ORDER BY training.startedDate DESC
""")
    List<Training> findAllFromDateAndPredefined(@Param("user") User user, @Param("from") ZonedDateTime from);

    boolean existsByCustomAlgorithmConfigurationIdAndIdNot(Integer cfgId, Integer id);

    // Get distinct predefined algorithms used by user
    @Query("""
    SELECT DISTINCT ac.algorithm FROM Training t
    JOIN t.algorithmConfiguration ac
    WHERE t.user = :user AND ac.algorithm IS NOT NULL
    """)
    List<com.cloud_ml_app_thesis.entity.Algorithm> findDistinctPredefinedAlgorithmsByUser(@Param("user") User user);

    // Get distinct custom algorithms used by user
    @Query("""
    SELECT DISTINCT cac.algorithm FROM Training t
    JOIN t.customAlgorithmConfiguration cac
    WHERE t.user = :user AND cac.algorithm IS NOT NULL
    """)
    List<com.cloud_ml_app_thesis.entity.CustomAlgorithm> findDistinctCustomAlgorithmsByUser(@Param("user") User user);

}
