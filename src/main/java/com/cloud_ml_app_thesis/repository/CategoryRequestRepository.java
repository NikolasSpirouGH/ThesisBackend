package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.CategoryRequest;
import com.cloud_ml_app_thesis.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CategoryRequestRepository extends JpaRepository<CategoryRequest, Integer> {

    void deleteByRequestedBy(User requestedBy);

    List<CategoryRequest> findByRequestedBy(User requestedBy);

}
