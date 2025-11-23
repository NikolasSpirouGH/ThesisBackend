package com.cloud_ml_app_thesis.repository.model;

import com.cloud_ml_app_thesis.entity.Category;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ModelRepository extends JpaRepository<Model, Integer> {
    @Query("""
            SELECT m FROM Model m
            WHERE m.accessibility.name = com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum.PUBLIC
            OR m.training.user.id = :userId
            OR EXISTS (
            SELECT s FROM ModelShare s
            WHERE s.model = m AND s.sharedWithUser.id = :userId
    )
""")
    List<Model> findAllAccessibleToUser(@Param("userId") UUID userId);

    List<Model> findByCategory(Category category);

    @Query("""
            SELECT DISTINCT m FROM Model m
            WHERE EXISTS (
                SELECT 1 FROM m.keywords k
                WHERE k IN :keywords
            )
            """)
    List<Model> findByKeywords(@Param("keywords") Set<String> keywords);

    Optional<Model> findByTraining(Training training);

    List<Model> findAllByTraining_User(User user);

    @Query("""
            SELECT m FROM Model m
            JOIN FETCH m.training t
            LEFT JOIN FETCH t.user u
            LEFT JOIN FETCH t.datasetConfiguration dc
            LEFT JOIN FETCH dc.dataset d
            LEFT JOIN FETCH t.algorithmConfiguration ac
            LEFT JOIN FETCH ac.algorithm alg
            LEFT JOIN FETCH ac.algorithmType at
            LEFT JOIN FETCH t.customAlgorithmConfiguration cac
            LEFT JOIN FETCH cac.algorithm calg
            WHERE m.id = :modelId
            """)
    Optional<Model> findByIdWithTrainingDetails(@Param("modelId") Integer modelId);

    Model findByName(String name);
}
