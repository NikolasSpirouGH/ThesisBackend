package com.backend.mlapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.backend.mlapp.entity.AppUser;
import org.springframework.lang.NonNullApi;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Integer> {

    Optional<AppUser> findByEmail(String username);

    Optional<AppUser> findById(Integer id);
}