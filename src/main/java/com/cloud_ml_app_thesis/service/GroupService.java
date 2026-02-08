package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.group.Group;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Set;

public interface GroupService {

    /**
     * Create a new group with the authenticated user as leader.
     */
    Group createGroup(String name, String description, UserDetails userDetails);

    /**
     * Add members to a group. Only the group leader or admin can do this.
     */
    void addMembersToGroup(Integer groupId, Set<String> usernames, UserDetails userDetails);

    /**
     * Remove members from a group. Only the group leader or admin can do this.
     */
    void removeMembersFromGroup(Integer groupId, Set<String> usernames, UserDetails userDetails);

    /**
     * Get all groups where the user is the leader.
     */
    List<Group> getGroupsAsLeader(UserDetails userDetails);

    /**
     * Get all groups where the user is a member (not leader).
     */
    List<Group> getGroupsAsMember(UserDetails userDetails);

    /**
     * Get all groups the user belongs to (as leader or member).
     */
    List<Group> getAllUserGroups(UserDetails userDetails);

    /**
     * Delete a group. Only the group leader or admin can do this.
     */
    void deleteGroup(Integer groupId, UserDetails userDetails);

    /**
     * Check if the user is the leader of the group.
     */
    boolean isGroupLeader(Integer groupId, User user);

    /**
     * Check if the user is a member of the group (including leader).
     */
    boolean isGroupMember(Integer groupId, User user);

    /**
     * Get a group by its ID.
     */
    Group getGroupById(Integer groupId);

    /**
     * Get all members of a group (including leader).
     */
    Set<User> getAllGroupMembers(Integer groupId);
}
