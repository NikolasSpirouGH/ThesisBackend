package com.cloud_ml_app_thesis.repository.model;

import com.cloud_ml_app_thesis.entity.group.Group;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.model.ModelGroupShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModelGroupShareRepository extends JpaRepository<ModelGroupShare, Integer> {

    Optional<ModelGroupShare> findByModelAndGroup(Model model, Group group);

    List<ModelGroupShare> findByModel(Model model);

    List<ModelGroupShare> findByGroup(Group group);

    void deleteByModelAndGroup(Model model, Group group);

    boolean existsByModelAndGroup(Model model, Group group);

    @Query("SELECT mgs FROM ModelGroupShare mgs WHERE mgs.model.id = :modelId AND :userId IN (SELECT m.id FROM mgs.group.members m)")
    List<ModelGroupShare> findByModelIdAndUserIsMember(@Param("modelId") Integer modelId, @Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(mgs) > 0 THEN true ELSE false END FROM ModelGroupShare mgs " +
           "WHERE mgs.model.id = :modelId AND " +
           "(:userId IN (SELECT m.id FROM mgs.group.members m) OR mgs.group.leader.id = :userId)")
    boolean hasUserAccessViaGroup(@Param("modelId") Integer modelId, @Param("userId") UUID userId);
}
