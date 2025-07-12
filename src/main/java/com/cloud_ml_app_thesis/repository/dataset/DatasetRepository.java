package com.cloud_ml_app_thesis.repository.dataset;
        
import com.cloud_ml_app_thesis.entity.Category;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.model.Model;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Integer>, JpaSpecificationExecutor<Dataset> {
    Optional<Dataset> findByFilePath(String fileUrl);
    Optional<Dataset> findByFileName(String fileName);
//    Optional<Dataset> findByTrainingId(Integer trainingID);
    Optional<List<Dataset>> findAllByUserUsername(String username);

    boolean existsByIdAndUserUsername(Integer datasetId, String username);
    boolean existsByIdAndUser(Integer datasetId, User user);

    List<Dataset> findByCategory(Category category);

}
