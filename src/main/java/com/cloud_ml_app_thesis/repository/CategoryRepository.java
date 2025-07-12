package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    Optional<Category> findByName(String name);

    @Query("SELECT c FROM Category c JOIN c.parentCategories p WHERE p.id = :parentId")
    Set<Category> findByParentCategoriesId(@Param("parentId") Integer parentId);

    boolean existsByName(String name);
}
