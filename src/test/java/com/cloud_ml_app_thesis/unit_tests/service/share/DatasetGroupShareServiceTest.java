package com.cloud_ml_app_thesis.unit_tests.service.share;

import com.cloud_ml_app_thesis.entity.Role;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.action.DatasetShareActionType;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.dataset.DatasetGroupShare;
import com.cloud_ml_app_thesis.entity.group.Group;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.action.DatasetShareActionTypeEnum;
import com.cloud_ml_app_thesis.helper.AuthorizationHelper;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.action.DatasetSareActionTypeRepository;
import com.cloud_ml_app_thesis.repository.dataset.*;
import com.cloud_ml_app_thesis.repository.group.GroupRepository;
import com.cloud_ml_app_thesis.service.DatasetShareService;
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
class DatasetGroupShareServiceTest {

    @Mock private DatasetRepository datasetRepository;
    @Mock private UserRepository userRepository;
    @Mock private DatasetShareRepository datasetShareRepository;
    @Mock private DatasetShareHistoryRepository datasetShareHistoryRepository;
    @Mock private DatasetCopyRepository datasetCopyRepository;
    @Mock private DatasetSareActionTypeRepository datasetSareActionTypeRepository;
    @Mock private DatasetGroupShareRepository datasetGroupShareRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private AuthorizationHelper authorizationHelper;

    @InjectMocks
    private DatasetShareService datasetShareService;

    private User ownerUser;
    private User memberUser;
    private User otherUser;
    private Dataset testDataset;
    private Group testGroup;
    private UserDetails ownerDetails;
    private UserDetails memberDetails;
    private DatasetShareActionType groupShareAction;
    private DatasetShareActionType groupUnshareAction;

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

        // Set up test dataset
        testDataset = new Dataset();
        testDataset.setId(1);
        testDataset.setUser(ownerUser);
        testDataset.setFileName("test_dataset.csv");
        testDataset.setOriginalFileName("test_dataset.csv");
        testDataset.setFilePath("datasets/test_dataset.csv");
        testDataset.setFileSize(1024L);
        testDataset.setContentType("text/csv");
        testDataset.setUploadDate(ZonedDateTime.now());

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
        groupShareAction = new DatasetShareActionType();
        groupShareAction.setId(4);
        groupShareAction.setName(DatasetShareActionTypeEnum.GROUP_SHARE);
        groupShareAction.setDescription("Dataset was shared with a group");

        groupUnshareAction = new DatasetShareActionType();
        groupUnshareAction.setId(5);
        groupUnshareAction.setName(DatasetShareActionTypeEnum.GROUP_UNSHARE);
        groupUnshareAction.setDescription("Dataset group sharing was revoked");
    }

    @Nested
    @DisplayName("Share Dataset with Group Tests")
    class ShareDatasetWithGroupTests {

        @Test
        @DisplayName("Should share dataset with group successfully as owner")
        void shareDatasetWithGroup_AsOwner_Success() {
            // Given
            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(ownerUser));
            when(datasetGroupShareRepository.existsByDatasetAndGroup(testDataset, testGroup)).thenReturn(false);
            when(datasetSareActionTypeRepository.findByName(DatasetShareActionTypeEnum.GROUP_SHARE))
                    .thenReturn(Optional.of(groupShareAction));
            when(datasetGroupShareRepository.save(any(DatasetGroupShare.class))).thenAnswer(invocation -> {
                DatasetGroupShare share = invocation.getArgument(0);
                share.setId(1);
                return share;
            });

            // When
            datasetShareService.shareDatasetWithGroup(1, 1, "owner", "Test comment");

            // Then
            verify(datasetGroupShareRepository).save(any(DatasetGroupShare.class));
            verify(datasetShareHistoryRepository).save(any());
        }

        @Test
        @DisplayName("Should throw exception when dataset not found")
        void shareDatasetWithGroup_DatasetNotFound_ThrowsException() {
            // Given
            when(datasetRepository.findById(999)).thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    datasetShareService.shareDatasetWithGroup(999, 1, "owner", null));
        }

        @Test
        @DisplayName("Should throw exception when group not found")
        void shareDatasetWithGroup_GroupNotFound_ThrowsException() {
            // Given
            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(groupRepository.findById(999)).thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    datasetShareService.shareDatasetWithGroup(1, 999, "owner", null));
        }

        @Test
        @DisplayName("Should throw exception when already shared with group")
        void shareDatasetWithGroup_AlreadyShared_ThrowsException() {
            // Given
            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(ownerUser));
            when(datasetGroupShareRepository.existsByDatasetAndGroup(testDataset, testGroup)).thenReturn(true);

            // When/Then
            assertThrows(IllegalStateException.class, () ->
                    datasetShareService.shareDatasetWithGroup(1, 1, "owner", null));
        }

        @Test
        @DisplayName("Should throw exception when non-owner tries to share")
        void shareDatasetWithGroup_NotOwner_ThrowsException() {
            // Given
            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
            when(datasetGroupShareRepository.existsByDatasetAndGroup(testDataset, testGroup)).thenReturn(false);

            // When/Then
            assertThrows(AccessDeniedException.class, () ->
                    datasetShareService.shareDatasetWithGroup(1, 1, "other", null));
        }
    }

    @Nested
    @DisplayName("Remove Group from Shared Dataset Tests")
    class RemoveGroupShareTests {

        @Test
        @DisplayName("Should remove group share successfully as owner")
        void removeGroupFromSharedDataset_AsOwner_Success() {
            // Given
            DatasetGroupShare existingShare = new DatasetGroupShare();
            existingShare.setId(1);
            existingShare.setDataset(testDataset);
            existingShare.setGroup(testGroup);
            existingShare.setSharedByUser(ownerUser);
            existingShare.setSharedAt(ZonedDateTime.now());

            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(ownerUser));
            when(authorizationHelper.isSuperDatasetUser(ownerDetails)).thenReturn(false);
            when(datasetGroupShareRepository.findByDatasetAndGroup(testDataset, testGroup))
                    .thenReturn(Optional.of(existingShare));
            when(datasetSareActionTypeRepository.findByName(DatasetShareActionTypeEnum.GROUP_UNSHARE))
                    .thenReturn(Optional.of(groupUnshareAction));

            // When
            datasetShareService.removeGroupFromSharedDataset(ownerDetails, 1, 1, "Revoking access");

            // Then
            verify(datasetGroupShareRepository).delete(existingShare);
            verify(datasetShareHistoryRepository).save(any());
        }

        @Test
        @DisplayName("Should throw exception when dataset not shared with group")
        void removeGroupFromSharedDataset_NotShared_ThrowsException() {
            // Given
            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("owner")).thenReturn(Optional.of(ownerUser));
            when(authorizationHelper.isSuperDatasetUser(ownerDetails)).thenReturn(false);
            when(datasetGroupShareRepository.findByDatasetAndGroup(testDataset, testGroup))
                    .thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    datasetShareService.removeGroupFromSharedDataset(ownerDetails, 1, 1, null));
        }

        @Test
        @DisplayName("Should throw exception when non-owner tries to remove share")
        void removeGroupFromSharedDataset_NotOwner_ThrowsException() {
            // Given
            UserDetails otherDetails = mock(UserDetails.class);
            when(otherDetails.getUsername()).thenReturn("other");

            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
            when(authorizationHelper.isSuperDatasetUser(otherDetails)).thenReturn(false);

            // When/Then
            assertThrows(AccessDeniedException.class, () ->
                    datasetShareService.removeGroupFromSharedDataset(otherDetails, 1, 1, null));
        }

        @Test
        @DisplayName("Should allow privileged user to remove share")
        void removeGroupFromSharedDataset_AsPrivilegedUser_Success() {
            // Given
            UserDetails adminDetails = mock(UserDetails.class);
            when(adminDetails.getUsername()).thenReturn("admin");

            User adminUser = new User();
            adminUser.setId(UUID.randomUUID());
            adminUser.setUsername("admin");

            DatasetGroupShare existingShare = new DatasetGroupShare();
            existingShare.setId(1);
            existingShare.setDataset(testDataset);
            existingShare.setGroup(testGroup);
            existingShare.setSharedByUser(ownerUser);
            existingShare.setSharedAt(ZonedDateTime.now());

            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(groupRepository.findById(1)).thenReturn(Optional.of(testGroup));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
            when(authorizationHelper.isSuperDatasetUser(adminDetails)).thenReturn(true);
            when(datasetGroupShareRepository.findByDatasetAndGroup(testDataset, testGroup))
                    .thenReturn(Optional.of(existingShare));
            when(datasetSareActionTypeRepository.findByName(DatasetShareActionTypeEnum.GROUP_UNSHARE))
                    .thenReturn(Optional.of(groupUnshareAction));

            // When
            datasetShareService.removeGroupFromSharedDataset(adminDetails, 1, 1, null);

            // Then
            verify(datasetGroupShareRepository).delete(existingShare);
        }
    }

    @Nested
    @DisplayName("Has Access to Dataset Tests")
    class HasAccessToDatasetTests {

        @Test
        @DisplayName("Should return true when user is owner")
        void hasAccessToDataset_AsOwner_ReturnsTrue() {
            // Given
            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));

            // When
            boolean result = datasetShareService.hasAccessToDataset(1, ownerUser);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return true when user has direct share")
        void hasAccessToDataset_WithDirectShare_ReturnsTrue() {
            // Given
            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(datasetShareRepository.findByDatasetAndSharedWithUser(testDataset, memberUser))
                    .thenReturn(Optional.of(mock(com.cloud_ml_app_thesis.entity.dataset.DatasetShare.class)));

            // When
            boolean result = datasetShareService.hasAccessToDataset(1, memberUser);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return true when user has group share access")
        void hasAccessToDataset_WithGroupShare_ReturnsTrue() {
            // Given
            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(datasetShareRepository.findByDatasetAndSharedWithUser(testDataset, memberUser))
                    .thenReturn(Optional.empty());
            when(datasetGroupShareRepository.hasUserAccessViaGroup(1, memberUser.getId())).thenReturn(true);

            // When
            boolean result = datasetShareService.hasAccessToDataset(1, memberUser);

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when user has no access")
        void hasAccessToDataset_NoAccess_ReturnsFalse() {
            // Given
            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(datasetShareRepository.findByDatasetAndSharedWithUser(testDataset, otherUser))
                    .thenReturn(Optional.empty());
            when(datasetGroupShareRepository.hasUserAccessViaGroup(1, otherUser.getId())).thenReturn(false);

            // When
            boolean result = datasetShareService.hasAccessToDataset(1, otherUser);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when dataset not found")
        void hasAccessToDataset_DatasetNotFound_ReturnsFalse() {
            // Given
            when(datasetRepository.findById(999)).thenReturn(Optional.empty());

            // When
            boolean result = datasetShareService.hasAccessToDataset(999, ownerUser);

            // Then
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Get Group Shares for Dataset Tests")
    class GetGroupSharesTests {

        @Test
        @DisplayName("Should get all group shares for dataset")
        void getGroupSharesForDataset_Success() {
            // Given
            DatasetGroupShare share1 = new DatasetGroupShare();
            share1.setId(1);
            share1.setDataset(testDataset);
            share1.setGroup(testGroup);

            when(datasetRepository.findById(1)).thenReturn(Optional.of(testDataset));
            when(datasetGroupShareRepository.findByDataset(testDataset)).thenReturn(List.of(share1));

            // When
            List<DatasetGroupShare> result = datasetShareService.getGroupSharesForDataset(1);

            // Then
            assertEquals(1, result.size());
            assertEquals(testGroup, result.get(0).getGroup());
        }

        @Test
        @DisplayName("Should throw exception when dataset not found")
        void getGroupSharesForDataset_DatasetNotFound_ThrowsException() {
            // Given
            when(datasetRepository.findById(999)).thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    datasetShareService.getGroupSharesForDataset(999));
        }
    }
}
