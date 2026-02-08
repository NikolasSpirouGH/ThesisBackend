package com.cloud_ml_app_thesis.unit_tests.controller;

import com.cloud_ml_app_thesis.controller.GroupController;
import com.cloud_ml_app_thesis.dto.request.group.GroupCreateRequest;
import com.cloud_ml_app_thesis.dto.request.group.GroupMemberRequest;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.group.Group;
import com.cloud_ml_app_thesis.service.GroupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.ZonedDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupControllerTest {

    @Mock
    private GroupService groupService;

    @InjectMocks
    private GroupController groupController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private User testLeader;
    private Group testGroup;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(groupController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        testLeader = new User();
        testLeader.setId(UUID.randomUUID());
        testLeader.setUsername("leader");
        testLeader.setEmail("leader@example.com");

        testGroup = Group.builder()
                .id(1)
                .name("Test Group")
                .description("A test group")
                .leader(testLeader)
                .members(new HashSet<>())
                .createdAt(ZonedDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("Create Group Tests")
    class CreateGroupTests {

        @Test
        @DisplayName("Should create group successfully")
        void createGroup_Success() throws Exception {
            // Given
            GroupCreateRequest request = new GroupCreateRequest();
            request.setName("New Group");
            request.setDescription("Description");

            when(groupService.createGroup(eq("New Group"), eq("Description"), any(UserDetails.class)))
                    .thenReturn(testGroup);

            // When/Then
            mockMvc.perform(post("/api/groups")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .principal(() -> "leader"))
                    .andExpect(status().isOk());

            verify(groupService).createGroup(eq("New Group"), eq("Description"), any(UserDetails.class));
        }
    }

    @Nested
    @DisplayName("Add Members Tests")
    class AddMembersTests {

        @Test
        @DisplayName("Should add members successfully")
        void addMembers_Success() throws Exception {
            // Given
            GroupMemberRequest request = new GroupMemberRequest();
            request.setUsernames(Set.of("user1", "user2"));

            doNothing().when(groupService).addMembersToGroup(eq(1), eq(Set.of("user1", "user2")), any(UserDetails.class));

            // When/Then
            mockMvc.perform(post("/api/groups/1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .principal(() -> "leader"))
                    .andExpect(status().isOk());

            verify(groupService).addMembersToGroup(eq(1), eq(Set.of("user1", "user2")), any(UserDetails.class));
        }
    }

    @Nested
    @DisplayName("Remove Members Tests")
    class RemoveMembersTests {

        @Test
        @DisplayName("Should remove members successfully")
        void removeMembers_Success() throws Exception {
            // Given
            GroupMemberRequest request = new GroupMemberRequest();
            request.setUsernames(Set.of("user1"));

            doNothing().when(groupService).removeMembersFromGroup(eq(1), eq(Set.of("user1")), any(UserDetails.class));

            // When/Then
            mockMvc.perform(delete("/api/groups/1/members")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .principal(() -> "leader"))
                    .andExpect(status().isOk());

            verify(groupService).removeMembersFromGroup(eq(1), eq(Set.of("user1")), any(UserDetails.class));
        }
    }

    @Nested
    @DisplayName("Get Groups Tests")
    class GetGroupsTests {

        @Test
        @DisplayName("Should get all user groups")
        void getAllUserGroups_Success() throws Exception {
            // Given
            when(groupService.getAllUserGroups(any(UserDetails.class))).thenReturn(List.of(testGroup));

            // When/Then
            mockMvc.perform(get("/api/groups/my-groups")
                            .principal(() -> "leader"))
                    .andExpect(status().isOk());

            verify(groupService).getAllUserGroups(any(UserDetails.class));
        }

        @Test
        @DisplayName("Should get groups as leader")
        void getGroupsAsLeader_Success() throws Exception {
            // Given
            when(groupService.getGroupsAsLeader(any(UserDetails.class))).thenReturn(List.of(testGroup));

            // When/Then
            mockMvc.perform(get("/api/groups/leading")
                            .principal(() -> "leader"))
                    .andExpect(status().isOk());

            verify(groupService).getGroupsAsLeader(any(UserDetails.class));
        }

        @Test
        @DisplayName("Should get groups as member")
        void getGroupsAsMember_Success() throws Exception {
            // Given
            when(groupService.getGroupsAsMember(any(UserDetails.class))).thenReturn(List.of(testGroup));

            // When/Then
            mockMvc.perform(get("/api/groups/member-of")
                            .principal(() -> "leader"))
                    .andExpect(status().isOk());

            verify(groupService).getGroupsAsMember(any(UserDetails.class));
        }
    }

    @Nested
    @DisplayName("Delete Group Tests")
    class DeleteGroupTests {

        @Test
        @DisplayName("Should delete group successfully")
        void deleteGroup_Success() throws Exception {
            // Given
            doNothing().when(groupService).deleteGroup(eq(1), any(UserDetails.class));

            // When/Then
            mockMvc.perform(delete("/api/groups/1")
                            .principal(() -> "leader"))
                    .andExpect(status().isOk());

            verify(groupService).deleteGroup(eq(1), any(UserDetails.class));
        }
    }

    @Nested
    @DisplayName("Get Group Details Tests")
    class GetGroupDetailsTests {

        @Test
        @DisplayName("Should get group by id")
        void getGroupById_Success() throws Exception {
            // Given
            when(groupService.getGroupById(eq(1))).thenReturn(testGroup);

            // When/Then
            mockMvc.perform(get("/api/groups/1")
                            .principal(() -> "leader"))
                    .andExpect(status().isOk());

            verify(groupService).getGroupById(eq(1));
        }
    }
}
