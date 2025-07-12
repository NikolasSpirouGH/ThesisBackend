package com.cloud_ml_app_thesis.repository.action;

import com.cloud_ml_app_thesis.entity.action.DatasetShareActionType;
import com.cloud_ml_app_thesis.enumeration.action.DatasetShareActionTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DatasetSareActionTypeRepository extends JpaRepository<DatasetShareActionType, Integer> {
    Optional<DatasetShareActionType> findByName(DatasetShareActionTypeEnum name);
}
