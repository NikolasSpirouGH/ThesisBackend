package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface TaskStatusRepository extends JpaRepository<AsyncTaskStatus, String> {
    Optional<AsyncTaskStatus> findByTaskId(String taskId);
    Optional<AsyncTaskStatus> findByTrainingId(Integer trainingId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE AsyncTaskStatus t SET t.stopRequested = :stop WHERE t.taskId = :taskId")
    int updateStopRequested(@Param("taskId") String taskId, @Param("stop") boolean stop);

    @Query("SELECT t.stopRequested FROM AsyncTaskStatus t WHERE t.taskId = :taskId")
    Boolean findStopRequested(@Param("taskId") String taskId);


    @Modifying
    @Query("""
    UPDATE AsyncTaskStatus t
    SET t.status = com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum.STOPPED,
        t.stopRequested = true,
        t.trainingId = :trainingId,
        t.modelId = :modelId,
        t.errorMessage = 'Task stopped by user',
        t.finishedAt = CURRENT_TIMESTAMP
    WHERE t.taskId = :taskId
""")
    void markTaskStopped(
            @Param("taskId") String taskId,
            @Param("trainingId") Integer trainingId,
            @Param("modelId") Integer modelId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE AsyncTaskStatus t SET t.jobName = :jobName WHERE t.taskId = :taskId")
    int updateJobName(@Param("taskId") String taskId, @Param("jobName") String jobName);

    @Query("SELECT t.jobName FROM AsyncTaskStatus t WHERE t.taskId = :taskId")
    String findJobName(@Param("taskId") String taskId);
}
