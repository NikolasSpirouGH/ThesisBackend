package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.JwtToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JwtTokenRepository extends JpaRepository<JwtToken, UUID> {
    Optional<JwtToken> findByToken(String token);

    boolean existsByToken(String jwt);
}