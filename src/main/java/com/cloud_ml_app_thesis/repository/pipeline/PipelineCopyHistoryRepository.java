package com.cloud_ml_app_thesis.repository.pipeline;

import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopy;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopyHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PipelineCopyHistoryRepository extends JpaRepository<PipelineCopyHistory, Long> {

    List<PipelineCopyHistory> findByPipelineCopyOrderByActionAtDesc(PipelineCopy pipelineCopy);

    List<PipelineCopyHistory> findByPipelineCopyIdOrderByActionAtDesc(Integer pipelineCopyId);
}
