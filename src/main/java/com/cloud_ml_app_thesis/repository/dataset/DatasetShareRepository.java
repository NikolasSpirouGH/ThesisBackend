package com.cloud_ml_app_thesis.repository.dataset;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.dataset.DatasetShare;
import org.springframework.data.jpa.repository.JpaRepository;

import javax.xml.crypto.Data;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DatasetShareRepository extends JpaRepository<DatasetShare, Integer> {
    Optional<DatasetShare> findByDatasetAndSharedWithUser(Dataset dataset, User sharedWithUser);
    Optional<DatasetShare> findByDatasetAndSharedWithUserUsername(Dataset dataset, String username);
    List<DatasetShare> findByDatasetAndSharedWithUserUsernameIn(Dataset dataset, Set<String> usernames);
    Set<DatasetShare> findByDatasetAndSharedWithUserIn(Dataset dataset, Set<User> usernames);

    void deleteById(Integer id);
    void deleteAllByDataset(Dataset dataset);
    void deleteByDatasetAndSharedWithUserIn(Dataset dataset, List<User> users);
    void deleteByDatasetAndSharedWithUserUsernameIn(Dataset dataset, Set<String> users);

    void deleteAllBySharedWithUserUsernameIn(Set<String> usernames);

}
