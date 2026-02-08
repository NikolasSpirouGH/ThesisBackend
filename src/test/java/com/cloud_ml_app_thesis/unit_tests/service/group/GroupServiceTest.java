package com.cloud_ml_app_thesis.unit_tests.service.group;

import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.group.Group;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.helper.AuthorizationHelper;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.group.GroupRepository;
import com.cloud_ml_app_thesis.service.impl.GroupServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthorizationHelper authorizationHelper;

    @InjectMocks
    private GroupServiceImpl groupService;

    private User leaderUser;
    private User memberUser;
    private User otherUser;
    private Group testGroup;
    private UserDetails leaderDetails;
    private UserDetails memberDetails;
    private UserDetails otherDetails;

    @BeforeEach
    void setUp() {
        // Set up leader user
        leaderUser = new User();
        leaderUser.setId(UUID.randomUUID());
        leaderUser.setUsername("leader");
        leaderUser.setEmail("leader@example.com");
        leaderUser.setRoles(Set.of(new Role(1, UserRoleEnum.USER, "User role", Set.of())));

        // Set up member user
        memberUser = new User();
        memberUser.setId(UUID.randomUUID());
        memberUser.setUsername("member");
        memberUser.setEmail("member@example.com");
        memberUser.setRoles(Set.of(new Role(1, UserRoleEnum.USER, "User role", Set.of())));

        // Set up other user (not in group)
        otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setUsername("other");
        otherUser.setEmail("other@example.com");
        otherUser.setRoles(Set.of(new Role(1, UserRoleEnum.USER, "User role", Set.of())));

        // Set up test group
        testGroup = Group.builder()
                .id(1)
                .name("Test Group")
                .description("A test group")
                .leader(leaderUser)
                .members(new HashSet<>(Set.of(memberUser)))
                .createdAt(ZonedDateTime.now())
                .build();

        // Set up UserDetails mocks
        leaderDetails = mock(UserDetails.class);
        when(leaderDetails.getUsername()).thenReturn("leader");

        memberDetails = mock(UserDetails.class);
        when(memberDetails.getUsername()).thenReturn("member");

        otherDetails = mock(UserDetails.class);
        when(otherDetails.getUsername()).thenReturn("other");
    }

    @Nested
    @DisplayName("Create Group Tests")
    class CreateGroupTests {

        @Test
        @DisplayName("Should create group successfully")
        void createGroup_Success() {
            // Given
            when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leaderUser));
            when(groupRepository.existsByName("New Group")).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
                Group group = invocation.getArgument(0);
                group.setId(1);
                return group;
            });

            // When
            Group result = groupService.createGroup("New Group", "Description", leaderDetails);

            // Then
            assertNotNull(result);
            assertEquals("New Group", result.getName());
            assertEquals("Description", result.getDescription());
            assertEquals(leaderUser, result.getLeader());
            verify(groupRepository).save(any(Group.class));
        }

        @Test
        @DisplayName("Should throw exception when group name already exists")
        void createGroup_NameExists_ThrowsException() {
            // Given
            when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leaderUser));
            when(groupRepository.existsByName("Existing Group")).thenReturn(true);

            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                    groupService.createGroup("Existing Group", "Description", leaderDetails));
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void createGroup_UserNotFound_ThrowsException() {
            // Given
            when(userRepository.findByUsername("leader")).thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    groupService.createGroup("New Group", "Description", leaderDetails));
        }
    }

    @Nested
    @DisplayName("Add Members Tests")
    class AddMembersTests {

        @Test
        @DisplayName("Should add members successfully as leader")
        void addMembers_AsLeader_Success() {
            // Given
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leaderUser));
            when(userRepository.findByUsernameIn(Set.of("other"))).thenReturn(List.of(otherUser));
            when(authorizationHelper.isAdmin(leaderDetails)).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            // When
            groupService.addMembersToGroup(1, Set.of("other"), leaderDetails);

            // Then
            verify(groupRepository).save(any(Group.class));
        }

        @Test
        @DisplayName("Should throw exception when non-leader tries to add members")
        void addMembers_AsNonLeader_ThrowsException() {
            // Given
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
            when(authorizationHelper.isAdmin(otherDetails)).thenReturn(false);

            // When/Then
            assertThrows(AccessDeniedException.class, () ->
                    groupService.addMembersToGroup(1, Set.of("newmember"), otherDetails));
        }

        @Test
        @DisplayName("Should allow admin to add members")
        void addMembers_AsAdmin_Success() {
            // Given
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
            when(userRepository.findByUsernameIn(Set.of("newmember"))).thenReturn(List.of(otherUser));
            when(authorizationHelper.isAdmin(otherDetails)).thenReturn(true);
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            // When
            groupService.addMembersToGroup(1, Set.of("newmember"), otherDetails);

            // Then
            verify(groupRepository).save(any(Group.class));
        }

        @Test
        @DisplayName("Should throw exception when some users not found")
        void addMembers_UsersNotFound_ThrowsException() {
            // Given
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leaderUser));
            when(userRepository.findByUsernameIn(Set.of("nonexistent"))).thenReturn(List.of());
            when(authorizationHelper.isAdmin(leaderDetails)).thenReturn(false);

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    groupService.addMembersToGroup(1, Set.of("nonexistent"), leaderDetails));
        }
    }

    @Nested
    @DisplayName("Remove Members Tests")
    class RemoveMembersTests {

        @Test
        @DisplayName("Should remove members successfully as leader")
        void removeMembers_AsLeader_Success() {
            // Given
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leaderUser));
            when(userRepository.findByUsernameIn(Set.of("member"))).thenReturn(List.of(memberUser));
            when(authorizationHelper.isAdmin(leaderDetails)).thenReturn(false);
            when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

            // When
            groupService.removeMembersFromGroup(1, Set.of("member"), leaderDetails);

            // Then
            verify(groupRepository).save(any(Group.class));
        }

        @Test
        @DisplayName("Should throw exception when non-leader tries to remove members")
        void removeMembers_AsNonLeader_ThrowsException() {
            // Given
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("member")).thenReturn(Optional.of(memberUser));
            when(authorizationHelper.isAdmin(memberDetails)).thenReturn(false);

            // When/Then
            assertThrows(AccessDeniedException.class, () ->
                    groupService.removeMembersFromGroup(1, Set.of("other"), memberDetails));
        }
    }

    @Nested
    @DisplayName("Get Groups Tests")
    class GetGroupsTests {

        @Test
        @DisplayName("Should get groups where user is leader")
        void getGroupsAsLeader_Success() {
            // Given
            when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leaderUser));
            when(groupRepository.findByLeader(leaderUser)).thenReturn(List.of(testGroup));

            // When
            List<Group> result = groupService.getGroupsAsLeader(leaderDetails);

            // Then
            assertEquals(1, result.size());
            assertEquals(testGroup, result.get(0));
        }

        @Test
        @DisplayName("Should get groups where user is member")
        void getGroupsAsMember_Success() {
            // Given
            when(userRepository.findByUsername("member")).thenReturn(Optional.of(memberUser));
            when(groupRepository.findGroupsByMemberId(memberUser.getId())).thenReturn(List.of(testGroup));

            // When
            List<Group> result = groupService.getGroupsAsMember(memberDetails);

            // Then
            assertEquals(1, result.size());
            assertEquals(testGroup, result.get(0));
        }

        @Test
        @DisplayName("Should get all groups for user")
        void getAllUserGroups_Success() {
            // Given
            when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leaderUser));
            when(groupRepository.findGroupsByUserIdAsLeaderOrMember(leaderUser.getId())).thenReturn(List.of(testGroup));

            // When
            List<Group> result = groupService.getAllUserGroups(leaderDetails);

            // Then
            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Delete Group Tests")
    class DeleteGroupTests {

        @Test
        @DisplayName("Should delete group as leader")
        void deleteGroup_AsLeader_Success() {
            // Given
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leaderUser));
            when(authorizationHelper.isAdmin(leaderDetails)).thenReturn(false);

            // When
            groupService.deleteGroup(1, leaderDetails);

            // Then
            verify(groupRepository).delete(testGroup);
        }

        @Test
        @DisplayName("Should throw exception when non-leader tries to delete")
        void deleteGroup_AsNonLeader_ThrowsException() {
            // Given
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("member")).thenReturn(Optional.of(memberUser));
            when(authorizationHelper.isAdmin(memberDetails)).thenReturn(false);

            // When/Then
            assertThrows(AccessDeniedException.class, () ->
                    groupService.deleteGroup(1, memberDetails));
        }

        @Test
        @DisplayName("Should allow admin to delete any group")
        void deleteGroup_AsAdmin_Success() {
            // Given
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
            when(authorizationHelper.isAdmin(otherDetails)).thenReturn(true);

            // When
            groupService.deleteGroup(1, otherDetails);

            // Then
            verify(groupRepository).delete(testGroup);
        }

        @Test
        @DisplayName("Should throw exception when group not found")
        void deleteGroup_NotFound_ThrowsException() {
            // Given
            when(groupRepository.findById(999)).thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    groupService.deleteGroup(999, leaderDetails));
        }
    }

    @Nested
    @DisplayName("Membership Check Tests")
    class MembershipCheckTests {

        @Test
        @DisplayName("Should return true when user is group leader")
        void isGroupLeader_True() {
            // Given
            when(groupRepository.isUserLeaderOfGroup(1, leaderUser.getId())).thenReturn(true);

            // When
            boolean result = groupService.isGroupLeader(1, leaderUser);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when user is not group leader")
        void isGroupLeader_False() {
            // Given
            when(groupRepository.isUserLeaderOfGroup(1, memberUser.getId())).thenReturn(false);

            // When
            boolean result = groupService.isGroupLeader(1, memberUser);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return true when user is group member")
        void isGroupMember_True() {
            // Given
            when(groupRepository.isUserMemberOfGroup(1, memberUser.getId())).thenReturn(true);

            // When
            boolean result = groupService.isGroupMember(1, memberUser);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return all group members including leader")
        void getAllGroupMembers_IncludesLeader() {
            // Given
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));

            // When
            Set<User> result = groupService.getAllGroupMembers(1);

            // Then
            assertEquals(2, result.size());
            assertTrue(result.contains(leaderUser));
            assertTrue(result.contains(memberUser));
        }
    }
}
