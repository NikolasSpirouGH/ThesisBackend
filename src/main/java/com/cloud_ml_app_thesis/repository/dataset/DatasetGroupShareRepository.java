package com.cloud_ml_app_thesis.repository.dataset;

import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.dataset.DatasetGroupShare;
import com.cloud_ml_app_thesis.entity.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DatasetGroupShareRepository extends JpaRepository<DatasetGroupShare, Integer> {

    Optional<DatasetGroupShare> findByDatasetAndGroup(Dataset dataset, Group group);

    List<DatasetGroupShare> findByDataset(Dataset dataset);

    List<DatasetGroupShare> findByGroup(Group group);

    void deleteByDatasetAndGroup(Dataset dataset, Group group);

    boolean existsByDatasetAndGroup(Dataset dataset, Group group);

    @Query("SELECT dgs FROM DatasetGroupShare dgs WHERE dgs.dataset.id = :datasetId AND :userId IN (SELECT m.id FROM dgs.group.members m)")
    List<DatasetGroupShare> findByDatasetIdAndUserIsMember(@Param("datasetId") Integer datasetId, @Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(dgs) > 0 THEN true ELSE false END FROM DatasetGroupShare dgs " +
           "WHERE dgs.dataset.id = :datasetId AND " +
           "(:userId IN (SELECT m.id FROM dgs.group.members m) OR dgs.group.leader.id = :userId)")
    boolean hasUserAccessViaGroup(@Param("datasetId") Integer datasetId, @Param("userId") UUID userId);
}
