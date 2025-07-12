package com.cloud_ml_app_thesis.repository.status;

import com.cloud_ml_app_thesis.entity.status.UserStatus;
import com.cloud_ml_app_thesis.enumeration.status.UserStatusEnum;
import com.cloud_ml_app_thesis.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserStatusRepository extends JpaRepository<com.cloud_ml_app_thesis.entity.status.UserStatus, Integer> {
    Optional<UserStatus> findByName(UserStatusEnum name);

}
