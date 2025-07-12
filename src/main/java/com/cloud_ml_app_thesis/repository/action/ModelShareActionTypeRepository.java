package com.cloud_ml_app_thesis.repository.action;

import com.cloud_ml_app_thesis.entity.action.ModelShareActionType;
import com.cloud_ml_app_thesis.enumeration.action.ModelShareActionTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelShareActionTypeRepository extends JpaRepository<ModelShareActionType, Integer> {
    Optional<ModelShareActionType> findByName(ModelShareActionTypeEnum name);
}
