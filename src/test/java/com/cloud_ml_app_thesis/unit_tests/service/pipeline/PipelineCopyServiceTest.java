package com.cloud_ml_app_thesis.unit_tests.service.pipeline;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.pipeline.PipelineCopyResponse;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.action.PipelineCopyActionType;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.group.Group;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopy;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopyMapping;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.PipelineEntityTypeEnum;
import com.cloud_ml_app_thesis.enumeration.action.PipelineCopyActionTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.PipelineCopyStatusEnum;
import com.cloud_ml_app_thesis.helper.AuthorizationHelper;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.action.PipelineCopyActionTypeRepository;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.group.GroupRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.pipeline.PipelineCopyHistoryRepository;
import com.cloud_ml_app_thesis.repository.pipeline.PipelineCopyMappingRepository;
import com.cloud_ml_app_thesis.repository.pipeline.PipelineCopyRepository;
import com.cloud_ml_app_thesis.service.GroupService;
import com.cloud_ml_app_thesis.service.MinioService;
import com.cloud_ml_app_thesis.service.ModelShareService;
import com.cloud_ml_app_thesis.service.impl.PipelineCopyServiceImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineCopyServiceTest {

    @Mock private TrainingRepository trainingRepository;
    @Mock private DatasetRepository datasetRepository;
    @Mock private DatasetConfigurationRepository datasetConfigurationRepository;
    @Mock private AlgorithmConfigurationRepository algorithmConfigurationRepository;
    @Mock private CustomAlgorithmConfigurationRepository customAlgorithmConfigurationRepository;
    @Mock private ModelRepository modelRepository;
    @Mock private UserRepository userRepository;
    @Mock private PipelineCopyRepository pipelineCopyRepository;
    @Mock private PipelineCopyMappingRepository pipelineCopyMappingRepository;
    @Mock private PipelineCopyHistoryRepository pipelineCopyHistoryRepository;
    @Mock private PipelineCopyActionTypeRepository actionTypeRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private MinioService minioService;
    @Mock private GroupService groupService;
    @Mock private ModelShareService modelShareService;
    @Mock private AuthorizationHelper authorizationHelper;
    @Mock private BucketResolver bucketResolver;

    @InjectMocks
    private PipelineCopyServiceImpl pipelineCopyService;

    private User sourceUser;
    private User targetUser;
    private Dataset sourceDataset;
    private DatasetConfiguration sourceDatasetConfig;
    private AlgorithmConfiguration sourceAlgoConfig;
    private Training sourceTraining;
    private Model sourceModel;
    private UserDetails sourceUserDetails;
    private PipelineCopyActionType initiatedAction;
    private PipelineCopyActionType completedAction;

    @BeforeEach
    void setUp() {
        // Set up source user
        sourceUser = new User();
        sourceUser.setId(UUID.randomUUID());
        sourceUser.setUsername("source_user");
        sourceUser.setEmail("source@example.com");

        // Set up target user
        targetUser = new User();
        targetUser.setId(UUID.randomUUID());
        targetUser.setUsername("target_user");
        targetUser.setEmail("target@example.com");

        // Set up dataset
        sourceDataset = new Dataset();
        sourceDataset.setId(1);
        sourceDataset.setUser(sourceUser);
        sourceDataset.setFileName("test_dataset.csv");
        sourceDataset.setOriginalFileName("test_dataset.csv");
        sourceDataset.setFilePath("datasets/test_dataset.csv");
        sourceDataset.setFileSize(1024L);
        sourceDataset.setContentType("text/csv");
        sourceDataset.setUploadDate(ZonedDateTime.now());

        // Set up dataset configuration
        sourceDatasetConfig = new DatasetConfiguration();
        sourceDatasetConfig.setId(1);
        sourceDatasetConfig.setDataset(sourceDataset);
        sourceDatasetConfig.setBasicAttributesColumns("col1,col2");
        sourceDatasetConfig.setTargetColumn("target");

        // Set up algorithm configuration
        sourceAlgoConfig = new AlgorithmConfiguration();
        sourceAlgoConfig.setId(1);
        sourceAlgoConfig.setUser(sourceUser);
        sourceAlgoConfig.setOptions("default options");

        // Set up training
        sourceTraining = Training.builder()
                .id(1)
                .user(sourceUser)
                .datasetConfiguration(sourceDatasetConfig)
                .algorithmConfiguration(sourceAlgoConfig)
                .startedDate(ZonedDateTime.now())
                .finishedDate(ZonedDateTime.now())
                .build();

        // Set up model
        sourceModel = Model.builder()
                .id(1)
                .training(sourceTraining)
                .modelUrl("models/model_1.pkl")
                .metricsUrl("metrics/metrics_1.json")
                .name("Test Model")
                .finalized(true)
                .createdAt(ZonedDateTime.now())
                .build();
        sourceTraining.setModel(sourceModel);

        // Set up UserDetails
        sourceUserDetails = mock(UserDetails.class);
        when(sourceUserDetails.getUsername()).thenReturn("source_user");

        // Set up action types
        initiatedAction = new PipelineCopyActionType(1, PipelineCopyActionTypeEnum.COPY_INITIATED, "Copy initiated");
        completedAction = new PipelineCopyActionType(2, PipelineCopyActionTypeEnum.COPY_COMPLETED, "Copy completed");
    }

    @Nested
    @DisplayName("Copy Pipeline Tests")
    class CopyPipelineTests {

        @Test
        @DisplayName("Should copy pipeline successfully to self")
        void copyPipeline_ToSelf_Success() {
            // Given
            when(trainingRepository.findById(1)).thenReturn(Optional.of(sourceTraining));
            when(userRepository.findByUsername("source_user")).thenReturn(Optional.of(sourceUser));
            when(authorizationHelper.isSuperModelUser(sourceUserDetails)).thenReturn(false);
            when(pipelineCopyRepository.existsBySourceTrainingAndCopyForUserAndStatus(
                    sourceTraining, sourceUser, PipelineCopyStatusEnum.COMPLETED)).thenReturn(false);

            when(pipelineCopyRepository.save(any(PipelineCopy.class))).thenAnswer(invocation -> {
                PipelineCopy copy = invocation.getArgument(0);
                copy.setId(1);
                return copy;
            });

            when(actionTypeRepository.findByName(PipelineCopyActionTypeEnum.COPY_INITIATED))
                    .thenReturn(Optional.of(initiatedAction));
            when(actionTypeRepository.findByName(PipelineCopyActionTypeEnum.COPY_COMPLETED))
                    .thenReturn(Optional.of(completedAction));

            when(bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET)).thenReturn("datasets");
            when(bucketResolver.resolve(BucketTypeEnum.MODEL)).thenReturn("models");
            when(bucketResolver.resolve(BucketTypeEnum.METRICS)).thenReturn("metrics");

            when(minioService.extractMinioKey(anyString())).thenAnswer(invocation -> {
                String path = invocation.getArgument(0);
                return path.contains("/") ? path.substring(path.indexOf("/") + 1) : path;
            });
            when(minioService.copyObject(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("copied_key");

            when(datasetRepository.save(any(Dataset.class))).thenAnswer(invocation -> {
                Dataset d = invocation.getArgument(0);
                d.setId(2);
                return d;
            });
            when(datasetConfigurationRepository.save(any(DatasetConfiguration.class))).thenAnswer(invocation -> {
                DatasetConfiguration dc = invocation.getArgument(0);
                dc.setId(2);
                return dc;
            });
            when(algorithmConfigurationRepository.save(any(AlgorithmConfiguration.class))).thenAnswer(invocation -> {
                AlgorithmConfiguration ac = invocation.getArgument(0);
                ac.setId(2);
                return ac;
            });
            when(trainingRepository.save(any(Training.class))).thenAnswer(invocation -> {
                Training t = invocation.getArgument(0);
                if (t.getId() == null) t.setId(2);
                return t;
            });
            when(modelRepository.save(any(Model.class))).thenAnswer(invocation -> {
                Model m = invocation.getArgument(0);
                m.setId(2);
                return m;
            });
            when(pipelineCopyMappingRepository.save(any(PipelineCopyMapping.class))).thenAnswer(invocation -> {
                PipelineCopyMapping m = invocation.getArgument(0);
                m.setId(1);
                return m;
            });

            // When
            PipelineCopyResponse result = pipelineCopyService.copyPipeline(1, null, sourceUserDetails);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getPipelineCopyId());
            assertEquals(1, result.getSourceTrainingId());
            assertEquals(PipelineCopyStatusEnum.COMPLETED, result.getStatus());
            assertNotNull(result.getMappings());

            // Verify MinIO copy was called
            verify(minioService, atLeastOnce()).copyObject(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should copy pipeline to another user")
        void copyPipeline_ToAnotherUser_Success() {
            // Given
            when(trainingRepository.findById(1)).thenReturn(Optional.of(sourceTraining));
            when(userRepository.findByUsername("source_user")).thenReturn(Optional.of(sourceUser));
            when(userRepository.findByUsername("target_user")).thenReturn(Optional.of(targetUser));
            when(authorizationHelper.isSuperModelUser(sourceUserDetails)).thenReturn(false);
            when(pipelineCopyRepository.existsBySourceTrainingAndCopyForUserAndStatus(
                    sourceTraining, targetUser, PipelineCopyStatusEnum.COMPLETED)).thenReturn(false);

            when(pipelineCopyRepository.save(any(PipelineCopy.class))).thenAnswer(invocation -> {
                PipelineCopy copy = invocation.getArgument(0);
                copy.setId(1);
                return copy;
            });

            when(actionTypeRepository.findByName(any())).thenReturn(Optional.of(initiatedAction));
            when(bucketResolver.resolve(any())).thenReturn("bucket");
            when(minioService.extractMinioKey(anyString())).thenReturn("key");
            when(minioService.copyObject(anyString(), anyString(), anyString(), anyString())).thenReturn("copied");

            when(datasetRepository.save(any(Dataset.class))).thenAnswer(inv -> {
                Dataset d = inv.getArgument(0);
                d.setId(2);
                return d;
            });
            when(datasetConfigurationRepository.save(any())).thenAnswer(inv -> {
                DatasetConfiguration dc = inv.getArgument(0);
                dc.setId(2);
                return dc;
            });
            when(algorithmConfigurationRepository.save(any())).thenAnswer(inv -> {
                AlgorithmConfiguration ac = inv.getArgument(0);
                ac.setId(2);
                return ac;
            });
            when(trainingRepository.save(any(Training.class))).thenAnswer(inv -> {
                Training t = inv.getArgument(0);
                if (t.getId() == null) t.setId(2);
                return t;
            });
            when(modelRepository.save(any())).thenAnswer(inv -> {
                Model m = inv.getArgument(0);
                m.setId(2);
                return m;
            });
            when(pipelineCopyMappingRepository.save(any())).thenAnswer(inv -> {
                PipelineCopyMapping m = inv.getArgument(0);
                m.setId(1);
                return m;
            });

            // When
            PipelineCopyResponse result = pipelineCopyService.copyPipeline(1, "target_user", sourceUserDetails);

            // Then
            assertNotNull(result);
            assertEquals("target_user", result.getCopyForUsername());
        }

        @Test
        @DisplayName("Should throw exception when training not found")
        void copyPipeline_TrainingNotFound_ThrowsException() {
            // Given
            when(trainingRepository.findById(999)).thenReturn(Optional.empty());

            // When/Then
            assertThrows(EntityNotFoundException.class, () ->
                    pipelineCopyService.copyPipeline(999, null, sourceUserDetails));
        }

        @Test
        @DisplayName("Should throw exception when already copied")
        void copyPipeline_AlreadyCopied_ThrowsException() {
            // Given
            when(trainingRepository.findById(1)).thenReturn(Optional.of(sourceTraining));
            when(userRepository.findByUsername("source_user")).thenReturn(Optional.of(sourceUser));
            when(authorizationHelper.isSuperModelUser(sourceUserDetails)).thenReturn(false);
            when(pipelineCopyRepository.existsBySourceTrainingAndCopyForUserAndStatus(
                    sourceTraining, sourceUser, PipelineCopyStatusEnum.COMPLETED)).thenReturn(true);

            // When/Then
            assertThrows(IllegalStateException.class, () ->
                    pipelineCopyService.copyPipeline(1, null, sourceUserDetails));
        }

        @Test
        @DisplayName("Should throw exception when not authorized")
        void copyPipeline_NotAuthorized_ThrowsException() {
            // Given
            UserDetails otherUserDetails = mock(UserDetails.class);
            when(otherUserDetails.getUsername()).thenReturn("other_user");

            User otherUser = new User();
            otherUser.setId(UUID.randomUUID());
            otherUser.setUsername("other_user");

            when(trainingRepository.findById(1)).thenReturn(Optional.of(sourceTraining));
            when(userRepository.findByUsername("other_user")).thenReturn(Optional.of(otherUser));
            when(authorizationHelper.isSuperModelUser(otherUserDetails)).thenReturn(false);
            when(modelShareService.hasAccessToModel(1, otherUser)).thenReturn(false);

            // When/Then
            assertThrows(AccessDeniedException.class, () ->
                    pipelineCopyService.copyPipeline(1, null, otherUserDetails));
        }
    }

    @Nested
    @DisplayName("Copy Pipeline to Group Tests")
    class CopyPipelineToGroupTests {

        @Test
        @DisplayName("Should copy pipeline to group members")
        void copyPipelineToGroup_Success() {
            // Given
            Group group = Group.builder()
                    .id(1)
                    .name("Test Group")
                    .leader(sourceUser)
                    .members(new HashSet<>(Set.of(targetUser)))
                    .build();

            when(groupRepository.findById(1)).thenReturn(Optional.of(group));
            when(userRepository.findByUsername("source_user")).thenReturn(Optional.of(sourceUser));
            when(authorizationHelper.isAdmin(sourceUserDetails)).thenReturn(false);
            when(groupService.getAllGroupMembers(1)).thenReturn(Set.of(sourceUser, targetUser));

            // Mock the copy operation for target user
            when(trainingRepository.findById(1)).thenReturn(Optional.of(sourceTraining));
            when(userRepository.findByUsername("target_user")).thenReturn(Optional.of(targetUser));
            when(authorizationHelper.isSuperModelUser(sourceUserDetails)).thenReturn(false);
            when(pipelineCopyRepository.existsBySourceTrainingAndCopyForUserAndStatus(
                    sourceTraining, targetUser, PipelineCopyStatusEnum.COMPLETED)).thenReturn(false);

            when(pipelineCopyRepository.save(any(PipelineCopy.class))).thenAnswer(invocation -> {
                PipelineCopy copy = invocation.getArgument(0);
                copy.setId(1);
                return copy;
            });

            when(actionTypeRepository.findByName(any())).thenReturn(Optional.of(initiatedAction));
            when(bucketResolver.resolve(any())).thenReturn("bucket");
            when(minioService.extractMinioKey(anyString())).thenReturn("key");
            when(minioService.copyObject(anyString(), anyString(), anyString(), anyString())).thenReturn("copied");

            when(datasetRepository.save(any())).thenAnswer(inv -> {
                Dataset d = inv.getArgument(0);
                d.setId(2);
                return d;
            });
            when(datasetConfigurationRepository.save(any())).thenAnswer(inv -> {
                DatasetConfiguration dc = inv.getArgument(0);
                dc.setId(2);
                return dc;
            });
            when(algorithmConfigurationRepository.save(any())).thenAnswer(inv -> {
                AlgorithmConfiguration ac = inv.getArgument(0);
                ac.setId(2);
                return ac;
            });
            when(trainingRepository.save(any(Training.class))).thenAnswer(inv -> {
                Training t = inv.getArgument(0);
                if (t.getId() == null) t.setId(2);
                return t;
            });
            when(modelRepository.save(any())).thenAnswer(inv -> {
                Model m = inv.getArgument(0);
                m.setId(2);
                return m;
            });
            when(pipelineCopyMappingRepository.save(any())).thenAnswer(inv -> {
                PipelineCopyMapping m = inv.getArgument(0);
                m.setId(1);
                return m;
            });

            // When
            List<PipelineCopyResponse> results = pipelineCopyService.copyPipelineToGroup(1, 1, sourceUserDetails);

            // Then
            assertNotNull(results);
            assertEquals(1, results.size()); // Only target user, not source (leader)
        }

        @Test
        @DisplayName("Should throw exception when not group leader")
        void copyPipelineToGroup_NotLeader_ThrowsException() {
            // Given
            UserDetails otherUserDetails = mock(UserDetails.class);
            when(otherUserDetails.getUsername()).thenReturn("other_user");

            User otherUser = new User();
            otherUser.setId(UUID.randomUUID());
            otherUser.setUsername("other_user");

            Group group = Group.builder()
                    .id(1)
                    .name("Test Group")
                    .leader(sourceUser)
                    .members(new HashSet<>(Set.of(targetUser)))
                    .build();

            when(groupRepository.findById(1)).thenReturn(Optional.of(group));
            when(userRepository.findByUsername("other_user")).thenReturn(Optional.of(otherUser));
            when(authorizationHelper.isAdmin(otherUserDetails)).thenReturn(false);

            // When/Then
            assertThrows(AccessDeniedException.class, () ->
                    pipelineCopyService.copyPipelineToGroup(1, 1, otherUserDetails));
        }
    }

    @Nested
    @DisplayName("Get Pipeline Copies Tests")
    class GetPipelineCopiesTests {

        @Test
        @DisplayName("Should get copies initiated by user")
        void getCopiesInitiatedByUser_Success() {
            // Given
            PipelineCopy copy = PipelineCopy.builder()
                    .id(1)
                    .sourceTraining(sourceTraining)
                    .copiedByUser(sourceUser)
                    .copyForUser(targetUser)
                    .status(PipelineCopyStatusEnum.COMPLETED)
                    .mappings(new ArrayList<>())
                    .build();

            when(userRepository.findByUsername("source_user")).thenReturn(Optional.of(sourceUser));
            when(pipelineCopyRepository.findByCopiedByUserIdOrderByCopyDateDesc(sourceUser.getId()))
                    .thenReturn(List.of(copy));

            // When
            List<PipelineCopy> results = pipelineCopyService.getCopiesInitiatedByUser(sourceUserDetails);

            // Then
            assertEquals(1, results.size());
            assertEquals(1, results.get(0).getId());
        }

        @Test
        @DisplayName("Should get copies received by user")
        void getCopiesReceivedByUser_Success() {
            // Given
            UserDetails targetUserDetails = mock(UserDetails.class);
            when(targetUserDetails.getUsername()).thenReturn("target_user");

            PipelineCopy copy = PipelineCopy.builder()
                    .id(1)
                    .sourceTraining(sourceTraining)
                    .copiedByUser(sourceUser)
                    .copyForUser(targetUser)
                    .status(PipelineCopyStatusEnum.COMPLETED)
                    .mappings(new ArrayList<>())
                    .build();

            when(userRepository.findByUsername("target_user")).thenReturn(Optional.of(targetUser));
            when(pipelineCopyRepository.findByCopyForUserIdOrderByCopyDateDesc(targetUser.getId()))
                    .thenReturn(List.of(copy));

            // When
            List<PipelineCopy> results = pipelineCopyService.getCopiesReceivedByUser(targetUserDetails);

            // Then
            assertEquals(1, results.size());
            assertEquals(1, results.get(0).getId());
        }

        @Test
        @DisplayName("Should get pipeline copy with mappings")
        void getPipelineCopyWithMappings_Success() {
            // Given
            PipelineCopyMapping mapping = PipelineCopyMapping.builder()
                    .id(1)
                    .entityType(PipelineEntityTypeEnum.DATASET)
                    .sourceEntityId(1)
                    .targetEntityId(2)
                    .build();

            PipelineCopy copy = PipelineCopy.builder()
                    .id(1)
                    .sourceTraining(sourceTraining)
                    .copiedByUser(sourceUser)
                    .copyForUser(targetUser)
                    .status(PipelineCopyStatusEnum.COMPLETED)
                    .mappings(new ArrayList<>(List.of(mapping)))
                    .build();

            when(pipelineCopyRepository.findByIdWithMappings(1)).thenReturn(copy);

            // When
            PipelineCopy result = pipelineCopyService.getPipelineCopyWithMappings(1);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getMappings().size());
            assertEquals(PipelineEntityTypeEnum.DATASET, result.getMappings().get(0).getEntityType());
        }
    }
}
