package com.backend.mlapp.repository;

import com.backend.mlapp.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRepository extends JpaRepository<VerificationToken, Integer> {
    VerificationToken findByToken(String token);
}
