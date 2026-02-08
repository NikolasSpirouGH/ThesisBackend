package com.cloud_ml_app_thesis.repository.pipeline;

import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopy;
import com.cloud_ml_app_thesis.enumeration.status.PipelineCopyStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PipelineCopyRepository extends JpaRepository<PipelineCopy, Integer> {

    List<PipelineCopy> findByCopiedByUser(User user);

    List<PipelineCopy> findByCopyForUser(User user);

    List<PipelineCopy> findBySourceTraining(Training training);

    List<PipelineCopy> findByStatus(PipelineCopyStatusEnum status);

    boolean existsBySourceTrainingAndCopyForUserAndStatus(
            Training sourceTraining, User copyForUser, PipelineCopyStatusEnum status);

    @Query("SELECT pc FROM PipelineCopy pc WHERE pc.copiedByUser.id = :userId ORDER BY pc.copyDate DESC")
    List<PipelineCopy> findByCopiedByUserIdOrderByCopyDateDesc(@Param("userId") UUID userId);

    @Query("SELECT pc FROM PipelineCopy pc WHERE pc.copyForUser.id = :userId ORDER BY pc.copyDate DESC")
    List<PipelineCopy> findByCopyForUserIdOrderByCopyDateDesc(@Param("userId") UUID userId);

    @Query("SELECT pc FROM PipelineCopy pc LEFT JOIN FETCH pc.mappings WHERE pc.id = :id")
    PipelineCopy findByIdWithMappings(@Param("id") Integer id);
}
