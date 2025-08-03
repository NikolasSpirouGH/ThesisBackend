package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.entity.action.DatasetShareActionType;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.dataset.DatasetShare;
import com.cloud_ml_app_thesis.entity.dataset.DatasetCopy;
import com.cloud_ml_app_thesis.entity.dataset.DatasetShareHistory;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.action.DatasetShareActionTypeEnum;
import com.cloud_ml_app_thesis.helper.AuthorizationHelper;
import com.cloud_ml_app_thesis.repository.action.DatasetSareActionTypeRepository;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.dataset.DatasetShareHistoryRepository;
import com.cloud_ml_app_thesis.repository.dataset.DatasetShareRepository;
import com.cloud_ml_app_thesis.repository.dataset.DatasetCopyRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.util.SecurityUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static com.cloud_ml_app_thesis.util.SecurityUtils.hasAnyRole;

@Service
@RequiredArgsConstructor
public class DatasetShareService {

    private final DatasetRepository datasetRepository;
    private final UserRepository userRepository;
    private final DatasetShareRepository datasetShareRepository;
    private final DatasetShareHistoryRepository datasetShareHistoryRepository;
    private final DatasetCopyRepository datasetCopyRepository;
    private final DatasetSareActionTypeRepository datasetSareActionTypeRepository;
    private final AuthorizationHelper authorizationHelper;
    /**
     * Share a dataset with a group of users
     */
    @Transactional //TODO Check if user has already shared the file with the targetUser
    public void shareDatasetWithUsers(Integer datasetId, Set<String> usernames, String sharedByUsername, String comment) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new EntityNotFoundException("Dataset not found"));

        User sharedByUser = userRepository.findByUsername(sharedByUsername)
                .orElseThrow(() -> new EntityNotFoundException("Sharing user not found"));

        List<User> users = userRepository.findByUsernameIn(usernames);
        if (users.size() != usernames.size()) {
            throw new EntityNotFoundException("One or more users not found");
        }

        List<DatasetShare> datasetSharesAlreadyExistSet = datasetShareRepository.findByDatasetAndSharedWithUserUsernameIn(dataset, usernames);
        if (datasetSharesAlreadyExistSet != null && !datasetSharesAlreadyExistSet.isEmpty()) {
            StringBuilder usernamesConcatenated = new StringBuilder();
            for (int i = 0; i < datasetSharesAlreadyExistSet.size() - 1; i++) {
                usernamesConcatenated.append(datasetSharesAlreadyExistSet.get(i).getSharedWithUser().getUsername());
                usernamesConcatenated.append(", ");
            }
            usernamesConcatenated.append(datasetSharesAlreadyExistSet.size() - 1);
            throw new RuntimeException("You have already shared this dataset with the users: " + usernamesConcatenated.toString());
        }

        for (User targetUser : users) {
            Optional<DatasetShare> existing = datasetShareRepository.findByDatasetAndSharedWithUser(dataset, targetUser);
            if (existing.isEmpty()) {
                DatasetShare share = new DatasetShare();
                share.setDataset(dataset);
                share.setSharedWithUser(targetUser);
                share.setSharedByUser(sharedByUser);
                share.setSharedAt(ZonedDateTime.now());
                share.setComment(comment);
                datasetShareRepository.save(share);
            }
        }
    }

    /**
     * Remove a group of users from shared dataset
     */
    @Transactional //TODO Check if user has shared the file with the targetUser
    public void removeUsersFromSharedDataset(UserDetails userDetails, Integer datasetId, Set<String> usernames, String comments) throws AccessDeniedException {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new EntityNotFoundException("Dataset not found"));

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        User sharedByUser = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User requested not found"));

        if (!roles.contains("ADMIN") && !roles.contains("DATASET_MANAGER")) {
            if (dataset.getUser().getUsername().equalsIgnoreCase(userDetails.getUsername())) {
                throw new AccessDeniedException("Unauthorized: You cannot modify datasource of other user");
            }
        }

        List<User> usersToRemove = userRepository.findByUsernameIn(usernames);
        if (usersToRemove.size() != usernames.size()) {
            throw new EntityNotFoundException("One or more users not found");
        }

        //Check if all usersToRemove are
        List<DatasetShare> datasetSharesAlreadyExistSet = datasetShareRepository.findByDatasetAndSharedWithUserUsernameIn(dataset, usernames);
        if (datasetSharesAlreadyExistSet.size() != usernames.size()) {
            throw new EntityNotFoundException("Be sure you are already sharing your dataset with the provided users");
        }

        DatasetShareActionType removeAction = datasetSareActionTypeRepository.findByName(DatasetShareActionTypeEnum.REMOVE)
                .orElseThrow(() -> new EntityNotFoundException("REMOVE action could not be found."));

        Set<DatasetShare> datasetShares = dataset.getDatasetShares();
        Set<DatasetShareHistory> datasetShareHistories = new HashSet<>();
        ZonedDateTime actionAt = ZonedDateTime.now(ZoneId.of("Europe/Athens"));

        Set<String> deletedUserUsernames = new HashSet<>();
        boolean deleteAllUsers = usernames == null || usernames.isEmpty();
        //Remove all users that have shared with the file
        if (deleteAllUsers) {
            //TODO check what deleteAllByDataset() does
            datasetShareRepository.deleteAllByDataset(dataset);
        } else {
            datasetShareRepository.deleteByDatasetAndSharedWithUserUsernameIn(dataset, usernames);
            deletedUserUsernames = usernames;
        }

        for (DatasetShare ds : datasetShares) {
            datasetShareHistories.add(new DatasetShareHistory(null, dataset, ds.getSharedWithUser(), sharedByUser, actionAt, removeAction, comments));
            if (deleteAllUsers) {
                deletedUserUsernames.add(ds.getSharedWithUser().getUsername());
            }
        }
        //TODO also check if I can fetch the shares also and how and why it is happening
        datasetShareHistoryRepository.saveAll(datasetShareHistories);
    }

    /**
     * Copy a shared dataset to the current user's ownership
     */
    @Transactional
    public Dataset copySharedDataset(Integer datasetId, User currentUser, String targetUsername) {
        Dataset originalDataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new EntityNotFoundException("Dataset not found"));

        boolean isPrivileged = hasAnyRole(currentUser, UserRoleEnum.ADMIN.toString(), UserRoleEnum.DATASET_MANAGER.toString());


        // Determine target user
        User targetUser = null;
        //copy a dataset that is shared with him case
        if(targetUsername == null || targetUsername.equals(currentUser.getUsername())){
            targetUser= currentUser;
            // If regular user, ensure the dataset is shared with them
            if (!isPrivileged) {
                datasetShareRepository.findByDatasetAndSharedWithUser(originalDataset, currentUser)
                        .orElseThrow(() -> new AccessDeniedException("Dataset is not shared with this user."));
            }
        } else { //The user must be privileged to copy for another
            //Prevent regular users from copying for others
            if(!isPrivileged){
                throw new AccessDeniedException("Only ADMIN or DATASET_MANAGER can copy for another user.");
            }
            targetUser = userRepository.findByUsername(targetUsername)
                    .orElseThrow(() -> new EntityNotFoundException("Target user not found"));
        }

        // Check if the dataset has already been copied by the target user
        if(hasUserCopiedDataset(originalDataset, targetUser)){
            //Maybe not RuntimeException
            throw new IllegalStateException("Dataset already copied.");
        }

        Dataset copy = new Dataset();
        copy.setUser(currentUser);
        copy.setOriginalFileName(originalDataset.getOriginalFileName());
        copy.setFileName("COPY_" + System.currentTimeMillis() + "_" + originalDataset.getFileName());
        copy.setFilePath(originalDataset.getFilePath()); // TODO: optionally duplicate the file physically
        copy.setFileSize(originalDataset.getFileSize());
        copy.setContentType(originalDataset.getContentType());
        copy.setUploadDate(ZonedDateTime.now());
        copy.setAccessibility(originalDataset.getAccessibility());
        copy.setCategory(originalDataset.getCategory());
        copy.setDescription("Copy of dataset ID " + originalDataset.getId());

        Dataset savedCopy = datasetRepository.save(copy);

        DatasetCopy copyLog = new DatasetCopy();
        copyLog.setOriginalDataset(originalDataset);
        copyLog.setCopiedBy(currentUser);
        copyLog.setCopyOperatedBy(currentUser);
        copyLog.setCopyDate(ZonedDateTime.now());
        datasetCopyRepository.save(copyLog);

        return savedCopy;
    }

    /**
     * Check if a user has already copied a dataset
     */
    private boolean hasUserCopiedDataset(Dataset dataset, User user) {
        return datasetCopyRepository.existsByOriginalDatasetAndCopiedBy(dataset, user);
    }

    public void declineDatasetShare(Integer datasetId, String targetUsername, String comments, UserDetails userDetails){
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new EntityNotFoundException("Dataset requested for reject does not exist."));
        String sharedWithUsername = userDetails.getUsername();

        if(targetUsername!= null && !targetUsername.isBlank()){
            // check if he is superuser
            if(!authorizationHelper.isSuperDatasetUser(userDetails)) {
                throw new AccessDeniedException("You are not authorized to reject Dataset Sharing for other user.");
            }
            sharedWithUsername = targetUsername;
        }

        User sharedWithUser = userRepository.findByUsername(sharedWithUsername)
                .orElseThrow(() -> new EntityNotFoundException("User could not be found"));

        User actionUser = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User could not be found"));
        //Check if the dataset is indeed shared
        DatasetShare datasetShare = datasetShareRepository.findByDatasetAndSharedWithUserUsername(dataset, sharedWithUsername)
                .orElseThrow(() -> new EntityNotFoundException("The dataset is not shared with the user"));

        datasetShareRepository.deleteById(datasetShare.getId());

        DatasetShareActionType declineAction = datasetSareActionTypeRepository.findByName(DatasetShareActionTypeEnum.DECLINED)
                .orElseThrow(() -> new EntityNotFoundException("REMOVE action could not be found."));

        datasetShareHistoryRepository.save(new DatasetShareHistory(null, dataset, sharedWithUser, actionUser, ZonedDateTime.now(ZoneId.of("Europe/Athens")), declineAction, comments));

    }
}

