package com.cloud_ml_app_thesis.service.impl;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.group.Group;
import com.cloud_ml_app_thesis.helper.AuthorizationHelper;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.group.GroupRepository;
import com.cloud_ml_app_thesis.service.GroupService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final AuthorizationHelper authorizationHelper;

    @Override
    public Group createGroup(String name, String description, UserDetails userDetails) {
        User leader = getUserFromDetails(userDetails);

        if (groupRepository.existsByName(name)) {
            throw new IllegalArgumentException("A group with this name already exists");
        }

        Group group = Group.builder()
                .name(name)
                .description(description)
                .leader(leader)
                .members(new HashSet<>())
                .createdAt(ZonedDateTime.now())
                .build();

        Group savedGroup = groupRepository.save(group);
        log.info("Created group '{}' with leader '{}'", name, leader.getUsername());
        return savedGroup;
    }

    @Override
    public void addMembersToGroup(Integer groupId, Set<String> usernames, UserDetails userDetails) {
        Group group = getGroupById(groupId);
        validateGroupLeaderOrAdmin(group, userDetails);

        List<User> usersToAdd = userRepository.findByUsernameIn(usernames);
        if (usersToAdd.size() != usernames.size()) {
            Set<String> foundUsernames = usersToAdd.stream()
                    .map(User::getUsername)
                    .collect(Collectors.toSet());
            Set<String> notFound = new HashSet<>(usernames);
            notFound.removeAll(foundUsernames);
            throw new EntityNotFoundException("Users not found: " + notFound);
        }

        // Don't add the leader as a member
        usersToAdd = usersToAdd.stream()
                .filter(u -> !u.getId().equals(group.getLeader().getId()))
                .toList();

        for (User user : usersToAdd) {
            group.addMember(user);
        }

        groupRepository.save(group);
        log.info("Added {} members to group '{}'", usersToAdd.size(), group.getName());
    }

    @Override
    public void removeMembersFromGroup(Integer groupId, Set<String> usernames, UserDetails userDetails) {
        Group group = getGroupById(groupId);
        validateGroupLeaderOrAdmin(group, userDetails);

        List<User> usersToRemove = userRepository.findByUsernameIn(usernames);

        for (User user : usersToRemove) {
            group.removeMember(user);
        }

        groupRepository.save(group);
        log.info("Removed {} members from group '{}'", usersToRemove.size(), group.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> getGroupsAsLeader(UserDetails userDetails) {
        User user = getUserFromDetails(userDetails);
        return groupRepository.findByLeader(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> getGroupsAsMember(UserDetails userDetails) {
        User user = getUserFromDetails(userDetails);
        return groupRepository.findGroupsByMemberId(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> getAllUserGroups(UserDetails userDetails) {
        User user = getUserFromDetails(userDetails);
        return groupRepository.findGroupsByUserIdAsLeaderOrMember(user.getId());
    }

    @Override
    public void deleteGroup(Integer groupId, UserDetails userDetails) {
        Group group = getGroupById(groupId);
        validateGroupLeaderOrAdmin(group, userDetails);

        groupRepository.delete(group);
        log.info("Deleted group '{}'", group.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isGroupLeader(Integer groupId, User user) {
        return groupRepository.isUserLeaderOfGroup(groupId, user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isGroupMember(Integer groupId, User user) {
        return groupRepository.isUserMemberOfGroup(groupId, user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Group getGroupById(Integer groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found with id: " + groupId));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<User> getAllGroupMembers(Integer groupId) {
        Group group = getGroupById(groupId);
        Set<User> allMembers = new HashSet<>(group.getMembers());
        allMembers.add(group.getLeader());
        return allMembers;
    }

    private User getUserFromDetails(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userDetails.getUsername()));
    }

    private void validateGroupLeaderOrAdmin(Group group, UserDetails userDetails) {
        User user = getUserFromDetails(userDetails);

        if (authorizationHelper.isAdmin(userDetails)) {
            return;
        }

        if (!group.getLeader().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the group leader or admin can perform this action");
        }
    }
}
