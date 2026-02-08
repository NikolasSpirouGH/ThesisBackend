package com.cloud_ml_app_thesis.repository.action;

import com.cloud_ml_app_thesis.entity.action.PipelineCopyActionType;
import com.cloud_ml_app_thesis.enumeration.action.PipelineCopyActionTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PipelineCopyActionTypeRepository extends JpaRepository<PipelineCopyActionType, Integer> {

    Optional<PipelineCopyActionType> findByName(PipelineCopyActionTypeEnum name);
}
