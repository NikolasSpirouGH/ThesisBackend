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
            WHERE m.status.name = 'PUBLIC'
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
            JOIN m.keywords k
            WHERE k.name IN :keywords
            """)
            List<Model> findByKeywords(@Param("keywords") Set<String> keywords);

    Optional<Model> findByTraining(Training training);

    List<Model> findAllByTraining_User(User user);
}
