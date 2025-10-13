package com.cloud_ml_app_thesis.repository;

import com.cloud_ml_app_thesis.entity.JwtToken;
import com.cloud_ml_app_thesis.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JwtTokenRepository extends JpaRepository<JwtToken, UUID> {
    Optional<JwtToken> findByToken(String token);

    boolean existsByToken(String jwt);

    @Query("SELECT t FROM JwtToken t WHERE t.user.id = :userId AND (t.expired = false OR t.revoked = false)")
    List<JwtToken> findValidTokensByUser(@Param("userId") UUID userId);

    List<JwtToken> findByUser(User user);
}