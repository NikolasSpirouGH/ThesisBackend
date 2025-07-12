package com.cloud_ml_app_thesis.repository.dataset;

import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.dataset.DatasetCopy;
import com.cloud_ml_app_thesis.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DatasetCopyRepository extends JpaRepository<DatasetCopy, Integer> {
    boolean existsByOriginalDatasetAndCopiedBy(Dataset dataset, User user);
}
