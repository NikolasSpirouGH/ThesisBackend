package com.cloud_ml_app_thesis.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.action.ModelShareActionType;
import com.cloud_ml_app_thesis.entity.group.Group;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.model.ModelGroupShare;
import com.cloud_ml_app_thesis.entity.model.ModelShare;
import com.cloud_ml_app_thesis.entity.model.ModelShareHistory;
import com.cloud_ml_app_thesis.enumeration.action.ModelShareActionTypeEnum;
import com.cloud_ml_app_thesis.helper.AuthorizationHelper;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.action.ModelShareActionTypeRepository;
import com.cloud_ml_app_thesis.repository.group.GroupRepository;
import com.cloud_ml_app_thesis.repository.model.ModelGroupShareRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.model.ModelShareHistoryRepository;
import com.cloud_ml_app_thesis.repository.model.ModelShareRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ModelShareService {

    private final ModelRepository modelRepository;
    private final UserRepository userRepository;
    private final ModelShareRepository modelShareRepository;
    private final ModelShareHistoryRepository modelShareHistoryRepository;
    private final ModelShareActionTypeRepository modelShareActionTypeRepository;
    private final ModelGroupShareRepository modelGroupShareRepository;
    private final GroupRepository groupRepository;
    private final AuthorizationHelper authorizationHelper;

    @Transactional
    public void shareModelWithUsers(Integer modelId, Set<String> usernames, String sharedByUsername, String comment) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        User sharedByUser = userRepository.findByUsername(sharedByUsername)
                .orElseThrow(() -> new EntityNotFoundException("Sharing user not found"));

        List<User> users = userRepository.findByUsernameIn(usernames);
        if (users.size() != usernames.size()) {
            throw new EntityNotFoundException("One or more users not found");
        }

        List<ModelShare> existingShares = modelShareRepository.findByModelAndSharedWithUserUsernameIn(model, usernames);
        if (!existingShares.isEmpty()) {
            throw new RuntimeException("You have already shared this model with some of the requested users.");
        }

        ModelShareActionType shareAction = modelShareActionTypeRepository.findByName(ModelShareActionTypeEnum.SHARE)
                .orElseThrow(() -> new EntityNotFoundException("SHARE action not found"));

        ZonedDateTime sharedAt = ZonedDateTime.now(ZoneId.of("Europe/Athens"));

        for (User targetUser : users) {
            ModelShare share = new ModelShare(null, model, targetUser, sharedByUser, sharedAt, comment);
            modelShareRepository.save(share);

            ModelShareHistory history = new ModelShareHistory(null, model, targetUser, sharedByUser, shareAction, sharedAt, comment);
            modelShareHistoryRepository.save(history);
        }
    }

    @Transactional
    public void revokeModelShares(UserDetails userDetails, Integer modelId, Set<String> usernames, String comments) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        User actionUser = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Only owner or superuser can revoke
        if (!authorizationHelper.isSuperModelUser(userDetails) &&
                !model.getTraining().getUser().getUsername().equalsIgnoreCase(userDetails.getUsername())) {
            throw new AccessDeniedException("Unauthorized to revoke model sharing.");
        }

        // Determine if we're revoking specific users or all
        boolean revokeAll = usernames == null || usernames.isEmpty();

        List<ModelShare> sharesToRevoke;
        if (revokeAll) {
            sharesToRevoke = modelShareRepository.findByModel(model);
            if (sharesToRevoke.isEmpty()) {
                throw new EntityNotFoundException("This model is not shared with any users.");
            }
        } else {
            List<User> usersToRemove = userRepository.findByUsernameIn(usernames);
            if (usersToRemove.size() != usernames.size()) {
                throw new EntityNotFoundException("Some users not found.");
            }

            sharesToRevoke = modelShareRepository.findByModelAndSharedWithUserUsernameIn(model, usernames);
            if (sharesToRevoke.size() != usernames.size()) {
                throw new EntityNotFoundException("Some users are not currently shared with this model.");
            }
        }

        // Delete shares and record history
        modelShareRepository.deleteAll(sharesToRevoke);

        ModelShareActionType revokeAction = modelShareActionTypeRepository
                .findByName(ModelShareActionTypeEnum.REVOKE)
                .orElseThrow(() -> new EntityNotFoundException("REVOKE action not found."));

        ZonedDateTime actionAt = ZonedDateTime.now(ZoneId.of("Europe/Athens"));
        List<ModelShareHistory> histories = new ArrayList<>();

        for (ModelShare share : sharesToRevoke) {
            histories.add(new ModelShareHistory(
                    null,
                    model,
                    share.getSharedWithUser(),
                    actionUser,
                    revokeAction,
                    actionAt,
                    comments
            ));
        }

        modelShareHistoryRepository.saveAll(histories);
    }

    /**
     * Share a model with a group.
     */
    @Transactional
    public void shareModelWithGroup(Integer modelId, Integer groupId, String sharedByUsername, String comment) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        User sharedByUser = userRepository.findByUsername(sharedByUsername)
                .orElseThrow(() -> new EntityNotFoundException("Sharing user not found"));

        // Check if already shared with this group
        if (modelGroupShareRepository.existsByModelAndGroup(model, group)) {
            throw new IllegalStateException("Model is already shared with this group");
        }

        // Validate that the sharer is the owner
        boolean isOwner = model.getTraining().getUser().getUsername().equalsIgnoreCase(sharedByUsername);

        if (!isOwner) {
            throw new AccessDeniedException("Only the model owner can share models with groups");
        }

        ModelGroupShare groupShare = new ModelGroupShare();
        groupShare.setModel(model);
        groupShare.setGroup(group);
        groupShare.setSharedByUser(sharedByUser);
        groupShare.setSharedAt(ZonedDateTime.now(ZoneId.of("Europe/Athens")));
        groupShare.setComment(comment);

        modelGroupShareRepository.save(groupShare);

        // Record in history
        ModelShareActionType groupShareAction = modelShareActionTypeRepository.findByName(ModelShareActionTypeEnum.GROUP_SHARE)
                .orElseThrow(() -> new EntityNotFoundException("GROUP_SHARE action type not found"));

        ModelShareHistory history = new ModelShareHistory(
                null, model, null, sharedByUser,
                groupShareAction,
                ZonedDateTime.now(ZoneId.of("Europe/Athens")),
                "Shared with group: " + group.getName() + (comment != null ? " - " + comment : "")
        );
        modelShareHistoryRepository.save(history);
    }

    /**
     * Remove group share from a model.
     */
    @Transactional
    public void revokeModelGroupShare(UserDetails userDetails, Integer modelId, Integer groupId, String comments) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        User actionUser = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Validate permission
        boolean isOwner = model.getTraining().getUser().getUsername().equalsIgnoreCase(userDetails.getUsername());
        boolean isPrivileged = authorizationHelper.isSuperModelUser(userDetails);

        if (!isOwner && !isPrivileged) {
            throw new AccessDeniedException("Only the model owner or privileged users can remove group sharing");
        }

        ModelGroupShare groupShare = modelGroupShareRepository.findByModelAndGroup(model, group)
                .orElseThrow(() -> new EntityNotFoundException("Model is not shared with this group"));

        modelGroupShareRepository.delete(groupShare);

        // Record in history
        ModelShareActionType unshareAction = modelShareActionTypeRepository.findByName(ModelShareActionTypeEnum.GROUP_UNSHARE)
                .orElseThrow(() -> new EntityNotFoundException("GROUP_UNSHARE action type not found"));

        ModelShareHistory history = new ModelShareHistory(
                null, model, null, actionUser,
                unshareAction,
                ZonedDateTime.now(ZoneId.of("Europe/Athens")),
                "Removed group share: " + group.getName() + (comments != null ? " - " + comments : "")
        );
        modelShareHistoryRepository.save(history);
    }

    /**
     * Check if a user has access to a model (via direct share OR group share).
     */
    public boolean hasAccessToModel(Integer modelId, User user) {
        Model model = modelRepository.findById(modelId).orElse(null);
        if (model == null) {
            return false;
        }

        // Owner always has access
        if (model.getTraining().getUser().getId().equals(user.getId())) {
            return true;
        }

        // Check direct share
        List<ModelShare> directShares = modelShareRepository.findByModelAndSharedWithUserUsernameIn(
                model, Set.of(user.getUsername()));
        if (!directShares.isEmpty()) {
            return true;
        }

        // Check group share
        return modelGroupShareRepository.hasUserAccessViaGroup(modelId, user.getId());
    }

    /**
     * Get all groups a model is shared with.
     */
    public List<ModelGroupShare> getGroupSharesForModel(Integer modelId) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));
        return modelGroupShareRepository.findByModel(model);
    }

}
