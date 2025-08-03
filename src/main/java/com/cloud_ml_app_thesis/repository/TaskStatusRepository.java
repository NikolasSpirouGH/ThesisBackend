package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskStatusRepository extends JpaRepository<AsyncTaskStatus, String> {
    Optional<AsyncTaskStatus> findByTaskId(String taskId);

    @Query(value = "SELECT stop_requested FROM async_task_status WHERE task_id = :taskId", nativeQuery = true)
    Boolean fetchFresh(@Param("taskId") String taskId);


}
