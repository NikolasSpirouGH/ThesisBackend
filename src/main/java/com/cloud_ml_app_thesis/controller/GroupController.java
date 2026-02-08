package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.group.GroupDTO;
import com.cloud_ml_app_thesis.dto.request.group.GroupCreateRequest;
import com.cloud_ml_app_thesis.dto.request.group.GroupMemberRequest;
import com.cloud_ml_app_thesis.entity.group.Group;
import com.cloud_ml_app_thesis.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GroupDTO> createGroup(
            @Valid @RequestBody GroupCreateRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        Group group = groupService.createGroup(request.getName(), request.getDescription(), accountDetails);

        if (request.getInitialMemberUsernames() != null && !request.getInitialMemberUsernames().isEmpty()) {
            groupService.addMembersToGroup(group.getId(), request.getInitialMemberUsernames(), accountDetails);
            group = groupService.getGroupById(group.getId());
        }

        return ResponseEntity.ok(mapToDTO(group));
    }

    @PostMapping("/{groupId}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GroupDTO> addMembers(
            @PathVariable Integer groupId,
            @Valid @RequestBody GroupMemberRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        groupService.addMembersToGroup(groupId, request.getUsernames(), accountDetails);
        Group group = groupService.getGroupById(groupId);
        return ResponseEntity.ok(mapToDTO(group));
    }

    @DeleteMapping("/{groupId}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GroupDTO> removeMembers(
            @PathVariable Integer groupId,
            @Valid @RequestBody GroupMemberRequest request,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        groupService.removeMembersFromGroup(groupId, request.getUsernames(), accountDetails);
        Group group = groupService.getGroupById(groupId);
        return ResponseEntity.ok(mapToDTO(group));
    }

    @GetMapping("/my-groups")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<GroupDTO>> getMyGroups(
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        List<Group> groups = groupService.getAllUserGroups(accountDetails);
        return ResponseEntity.ok(groups.stream().map(this::mapToDTO).collect(Collectors.toList()));
    }

    @GetMapping("/leading")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<GroupDTO>> getGroupsAsLeader(
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        List<Group> groups = groupService.getGroupsAsLeader(accountDetails);
        return ResponseEntity.ok(groups.stream().map(this::mapToDTO).collect(Collectors.toList()));
    }

    @GetMapping("/member-of")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<GroupDTO>> getGroupsAsMember(
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        List<Group> groups = groupService.getGroupsAsMember(accountDetails);
        return ResponseEntity.ok(groups.stream().map(this::mapToDTO).collect(Collectors.toList()));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GroupDTO> getGroup(@PathVariable Integer groupId) {
        Group group = groupService.getGroupById(groupId);
        return ResponseEntity.ok(mapToDTO(group));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable Integer groupId,
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        groupService.deleteGroup(groupId, accountDetails);
        return ResponseEntity.ok().build();
    }

    private GroupDTO mapToDTO(Group group) {
        return GroupDTO.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .leaderUsername(group.getLeader().getUsername())
                .memberUsernames(group.getMembers().stream()
                        .map(u -> u.getUsername())
                        .collect(Collectors.toList()))
                .createdAt(group.getCreatedAt())
                .memberCount(group.getMembers().size())
                .build();
    }
}
