package com.cloud_ml_app_thesis.unit_tests.service.share;

import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.action.ModelShareActionType;
import com.cloud_ml_app_thesis.entity.group.Group;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.model.ModelGroupShare;
import com.cloud_ml_app_thesis.entity.model.ModelShare;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.action.ModelShareActionTypeEnum;
import com.cloud_ml_app_thesis.helper.AuthorizationHelper;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.action.ModelShareActionTypeRepository;
import com.cloud_ml_app_thesis.repository.group.GroupRepository;
import com.cloud_ml_app_thesis.repository.model.ModelGroupShareRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.model.ModelShareHistoryRepository;
import com.cloud_ml_app_thesis.repository.model.ModelShareRepository;
import com.cloud_ml_app_thesis.service.ModelShareService;
import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelGroupShareServiceTest {

    @Mock private ModelRepository modelRepository;
    @Mock private UserRepository userRepository;
    @Mock private ModelShareRepository modelShareRepository;
    @Mock private ModelShareHistoryRepository modelShareHistoryRepository;
    @Mock private ModelShareActionTypeRepository modelShareActionTypeRepository;
    @Mock private ModelGroupShareRepository modelGroupShareRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private AuthorizationHelper authorizationHelper;

    @InjectMocks
    private ModelShareService modelShareService;

    private User ownerUser;
    private User memberUser;
    private User otherUser;
    private Training testTraining;
    private Model testModel;
    private Group testGroup;
    private UserDetails ownerDetails;
    private UserDetails memberDetails;
    private ModelShareActionType groupShareAction;
    private ModelShareActionType groupUnshareAction;

    @BeforeEach
    void setUp() {
        // Set up owner user
        ownerUser = new User();
        ownerUser.setId(UUID.randomUUID());
        ownerUser.setUsername("owner");
        ownerUser.setEmail("owner@example.com");
        ownerUser.setRoles(Set.of(new Role(1, UserRoleEnum.USER, "User role", Set.of())));

        // Set up member user
        memberUser = new User();
        memberUser.setId(UUID.randomUUID());
        memberUser.setUsername("member");
        memberUser.setEmail("member@example.com");
        memberUser.setRoles(Set.of(new Role(1, UserRoleEnum.USER, "User role", Set.of())));

        // Set up other user
        otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setUsername("other");
        otherUser.setEmail("other@example.com");
        otherUser.setRoles(Set.of(new Role(1, UserRoleEnum.USER, "User role", Set.of())));

        // Set up test training
        testTraining = new Training();
        testTraining.setId(1);
        testTraining.setUser(ownerUser);

        // Set up test model
        testModel = new Model();
        testModel.setId(1);
        testModel.setTraining(testTraining);
        testModel.setModelUrl("models/test_model.pkl");

        // Set up test group
        testGroup = Group.builder()
                .id(1)
                .name("Test Group")
                .description("A test group")
                .leader(ownerUser)
                .members(new HashSet<>(Set.of(memberUser)))
                .createdAt(ZonedDateTime.now())
                .build();

        // Set up UserDetails mocks
        ownerDetails = mock(UserDetails.class);
        when(ownerDetails.getUsername()).thenReturn("owner");

        memberDetails = mock(UserDetails.class);
        when(memberDetails.getUsername()).thenReturn("member");

        // Set up action types
        groupShareAction = new ModelShareActionType();
        groupShareAction.setId(4);
        groupShareAction.setName(ModelShareActionTypeEnum.GROUP_SHARE);
        groupShareAction.setDescription("Model was shared with a group");

        groupUnshareAction = new ModelShareActionType();
        groupUnshareAction.setId(5);
        groupUnshareAction.setName(ModelShareActionTypeEnum.GROUP_UNSHARE);
        groupUnshareAction.setDescription("Model group sharing was revoked");
    }

    @Nested
    @DisplayName("Share Model with Group Tests")
    class ShareModelWithGroupTests {

        @Test
        @DisplayName("Should share model with group successfully as owner")
        void shareModelWithGroup_AsOwner_Success() {
            // Given
            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(ownerUser));
            when(modelGroupShareRepository.existsByModelAndGroup(testModel, testGroup)).thenReturn(false);
            when(modelShareActionTypeRepository.findByName(ModelShareActionTypeEnum.GROUP_SHARE))
                    .thenReturn(Optional.of(groupShareAction));
            when(modelGroupShareRepository.save(any(ModelGroupShare.class))).thenAnswer(invocation -> {
                ModelGroupShare share = invocation.getArgument(0);
                share.setId(1);
                return share;
            });

            // When
            modelShareService.shareModelWithGroup(1, 1, "owner", "Test comment");

            // Then
            verify(modelGroupShareRepository).save(any(ModelGroupShare.class));
            verify(modelShareHistoryRepository).save(any());
        }

        @Test
        @DisplayName("Should throw exception when model not found")
        void shareModelWithGroup_ModelNotFound_ThrowsException() {
            // Given
            when(modelRepository.findById(999)).thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    modelShareService.shareModelWithGroup(999, 1, "owner", null));
        }

        @Test
        @DisplayName("Should throw exception when group not found")
        void shareModelWithGroup_GroupNotFound_ThrowsException() {
            // Given
            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(groupRepository.findById(999)).thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    modelShareService.shareModelWithGroup(1, 999, "owner", null));
        }

        @Test
        @DisplayName("Should throw exception when already shared with group")
        void shareModelWithGroup_AlreadyShared_ThrowsException() {
            // Given
            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(ownerUser));
            when(modelGroupShareRepository.existsByModelAndGroup(testModel, testGroup)).thenReturn(true);

            // When/Then
            assertThrows(IllegalStateException.class, () ->
                    modelShareService.shareModelWithGroup(1, 1, "owner", null));
        }

        @Test
        @DisplayName("Should throw exception when non-owner tries to share")
        void shareModelWithGroup_NotOwner_ThrowsException() {
            // Given
            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
            when(modelGroupShareRepository.existsByModelAndGroup(testModel, testGroup)).thenReturn(false);

            // When/Then
            assertThrows(AccessDeniedException.class, () ->
                    modelShareService.shareModelWithGroup(1, 1, "other", null));
        }
    }

    @Nested
    @DisplayName("Revoke Model Group Share Tests")
    class RevokeModelGroupShareTests {

        @Test
        @DisplayName("Should revoke group share successfully as owner")
        void revokeModelGroupShare_AsOwner_Success() {
            // Given
            ModelGroupShare existingShare = new ModelGroupShare();
            existingShare.setId(1);
            existingShare.setModel(testModel);
            existingShare.setGroup(testGroup);
            existingShare.setSharedByUser(ownerUser);
            existingShare.setSharedAt(ZonedDateTime.now());

            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(ownerUser));
            when(authorizationHelper.isSuperModelUser(ownerDetails)).thenReturn(false);
            when(modelGroupShareRepository.findByModelAndGroup(testModel, testGroup))
                    .thenReturn(Optional.of(existingShare));
            when(modelShareActionTypeRepository.findByName(ModelShareActionTypeEnum.GROUP_UNSHARE))
                    .thenReturn(Optional.of(groupUnshareAction));

            // When
            modelShareService.revokeModelGroupShare(ownerDetails, 1, 1, "Revoking access");

            // Then
            verify(modelGroupShareRepository).delete(existingShare);
            verify(modelShareHistoryRepository).save(any());
        }

        @Test
        @DisplayName("Should throw exception when model not shared with group")
        void revokeModelGroupShare_NotShared_ThrowsException() {
            // Given
            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(ownerUser));
            when(authorizationHelper.isSuperModelUser(ownerDetails)).thenReturn(false);
            when(modelGroupShareRepository.findByModelAndGroup(testModel, testGroup))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    modelShareService.revokeModelGroupShare(ownerDetails, 1, 1, null));
        }

        @Test
        @DisplayName("Should throw exception when non-owner tries to revoke share")
        void revokeModelGroupShare_NotOwner_ThrowsException() {
            // Given
            UserDetails otherDetails = mock(UserDetails.class);
            when(otherDetails.getUsername()).thenReturn("other");

            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
            when(authorizationHelper.isSuperModelUser(otherDetails)).thenReturn(false);

            // When/Then
            assertThrows(AccessDeniedException.class, () ->
                    modelShareService.revokeModelGroupShare(otherDetails, 1, 1, null));
        }

        @Test
        @DisplayName("Should allow privileged user to revoke share")
        void revokeModelGroupShare_AsPrivilegedUser_Success() {
            // Given
            UserDetails adminDetails = mock(UserDetails.class);
            when(adminDetails.getUsername()).thenReturn("admin");

            User adminUser = new User();
            adminUser.setId(UUID.randomUUID());
            adminUser.setUsername("admin");

            ModelGroupShare existingShare = new ModelGroupShare();
            existingShare.setId(1);
            existingShare.setModel(testModel);
            existingShare.setGroup(testGroup);
            existingShare.setSharedByUser(ownerUser);
            existingShare.setSharedAt(ZonedDateTime.now());

            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(authorizationHelper.isSuperModelUser(adminDetails)).thenReturn(true);
            when(modelGroupShareRepository.findByModelAndGroup(testModel, testGroup))
                    .thenReturn(Optional.of(existingShare));
            when(modelShareActionTypeRepository.findByName(ModelShareActionTypeEnum.GROUP_UNSHARE))
                    .thenReturn(Optional.of(groupUnshareAction));

            // When
            modelShareService.revokeModelGroupShare(adminDetails, 1, 1, null);

            // Then
            verify(modelGroupShareRepository).delete(existingShare);
        }
    }

    @Nested
    @DisplayName("Has Access to Model Tests")
    class HasAccessToModelTests {

        @Test
        @DisplayName("Should return true when user is owner")
        void hasAccessToModel_AsOwner_ReturnsTrue() {
            // Given
            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));

            // When
            boolean result = modelShareService.hasAccessToModel(1, ownerUser);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return true when user has direct share")
        void hasAccessToModel_WithDirectShare_ReturnsTrue() {
            // Given
            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(modelShareRepository.findByModelAndSharedWithUserUsernameIn(testModel, Set.of("member")))
                    .thenReturn(List.of(mock(ModelShare.class)));

            // When
            boolean result = modelShareService.hasAccessToModel(1, memberUser);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return true when user has group share access")
        void hasAccessToModel_WithGroupShare_ReturnsTrue() {
            // Given
            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(modelShareRepository.findByModelAndSharedWithUserUsernameIn(testModel, Set.of("member")))
                    .thenReturn(List.of());
            when(modelGroupShareRepository.hasUserAccessViaGroup(1, memberUser.getId())).thenReturn(true);

            // When
            boolean result = modelShareService.hasAccessToModel(1, memberUser);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when user has no access")
        void hasAccessToModel_NoAccess_ReturnsFalse() {
            // Given
            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(modelShareRepository.findByModelAndSharedWithUserUsernameIn(testModel, Set.of("other")))
                    .thenReturn(List.of());
            when(modelGroupShareRepository.hasUserAccessViaGroup(1, otherUser.getId())).thenReturn(false);

            // When
            boolean result = modelShareService.hasAccessToModel(1, otherUser);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when model not found")
        void hasAccessToModel_ModelNotFound_ReturnsFalse() {
            // Given
            when(modelRepository.findById(999)).thenReturn(Optional.empty());

            // When
            boolean result = modelShareService.hasAccessToModel(999, ownerUser);

            // Then
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Get Group Shares for Model Tests")
    class GetGroupSharesTests {

        @Test
        @DisplayName("Should get all group shares for model")
        void getGroupSharesForModel_Success() {
            // Given
            ModelGroupShare share1 = new ModelGroupShare();
            share1.setId(1);
            share1.setModel(testModel);
            share1.setGroup(testGroup);

            when(modelRepository.findById(1)).thenReturn(Optional.of(testModel));
            when(modelGroupShareRepository.findByModel(testModel)).thenReturn(List.of(share1));

            // When
            List<ModelGroupShare> result = modelShareService.getGroupSharesForModel(1);

            // Then
            assertEquals(1, result.size());
            assertEquals(testGroup, result.get(0).getGroup());
        }

        @Test
        @DisplayName("Should throw exception when model not found")
        void getGroupSharesForModel_ModelNotFound_ThrowsException() {
            // Given
            when(modelRepository.findById(999)).thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    modelShareService.getGroupSharesForModel(999));
        }
    }
}
