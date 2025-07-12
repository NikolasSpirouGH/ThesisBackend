package com.cloud_ml_app_thesis.repository.model;

import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.model.ModelShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface ModelShareRepository extends JpaRepository<ModelShare, Integer> {
    List<ModelShare> findByModel(Model model);
    List<ModelShare> findByModelAndSharedWithUserUsernameIn(Model model, Set<String> usernames);
    void deleteByModelAndSharedWithUserUsernameIn(Model model, Set<String> usernames);

}
