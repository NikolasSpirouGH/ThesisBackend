package com.cloud_ml_app_thesis.repository.pipeline;

import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopy;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopyMapping;
import com.cloud_ml_app_thesis.enumeration.PipelineEntityTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PipelineCopyMappingRepository extends JpaRepository<PipelineCopyMapping, Integer> {

    List<PipelineCopyMapping> findByPipelineCopy(PipelineCopy pipelineCopy);

    Optional<PipelineCopyMapping> findByPipelineCopyAndEntityTypeAndSourceEntityId(
            PipelineCopy pipelineCopy, PipelineEntityTypeEnum entityType, Integer sourceEntityId);

    List<PipelineCopyMapping> findByPipelineCopyAndEntityType(
            PipelineCopy pipelineCopy, PipelineEntityTypeEnum entityType);
}
