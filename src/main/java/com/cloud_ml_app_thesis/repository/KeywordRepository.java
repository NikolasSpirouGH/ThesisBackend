package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.model.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {
    Optional<Keyword> findByName(String name);
    Optional<Keyword> findByNameIgnoreCase(String name);
}
