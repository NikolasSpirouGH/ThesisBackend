package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskStatusRepository extends JpaRepository<AsyncTaskStatus, String> {


    @Query("select t.modelId from AsyncTaskStatus t where t.taskId =:taskId")
   Optional<Integer> findModelIdByTaskId(@Param("taskId") String taskId);
}
