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
    Optional<List<Dataset>> findAllByUserUsername(String username);

    Optional<Dataset> findById(Integer id);
    List<Dataset> findByCategory(Category category);

}
