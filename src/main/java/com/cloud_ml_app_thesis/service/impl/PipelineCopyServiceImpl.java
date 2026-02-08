package com.cloud_ml_app_thesis.service.impl;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.pipeline.PipelineCopyResponse;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.action.PipelineCopyActionType;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.group.Group;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopy;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopyHistory;
import com.cloud_ml_app_thesis.entity.pipeline.PipelineCopyMapping;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.PipelineEntityTypeEnum;
import com.cloud_ml_app_thesis.enumeration.action.PipelineCopyActionTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.PipelineCopyMappingStatusEnum;
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
import com.cloud_ml_app_thesis.service.PipelineCopyService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PipelineCopyServiceImpl implements PipelineCopyService {

    private final TrainingRepository trainingRepository;
    private final DatasetRepository datasetRepository;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final AlgorithmConfigurationRepository algorithmConfigurationRepository;
    private final CustomAlgorithmConfigurationRepository customAlgorithmConfigurationRepository;
    private final ModelRepository modelRepository;
    private final UserRepository userRepository;
    private final PipelineCopyRepository pipelineCopyRepository;
    private final PipelineCopyMappingRepository pipelineCopyMappingRepository;
    private final PipelineCopyHistoryRepository pipelineCopyHistoryRepository;
    private final PipelineCopyActionTypeRepository actionTypeRepository;
    private final GroupRepository groupRepository;
    private final MinioService minioService;
    private final GroupService groupService;
    private final ModelShareService modelShareService;
    private final AuthorizationHelper authorizationHelper;
    private final BucketResolver bucketResolver;

    @Override
    public PipelineCopyResponse copyPipeline(Integer trainingId, String targetUsername, UserDetails userDetails) {
        // 1. Load source training with all relationships
        Training sourceTraining = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new EntityNotFoundException("Training not found with id: " + trainingId));

        // 2. Authorization check
        validateCopyPermission(sourceTraining, userDetails);

        // 3. Load users
        User currentUser = getUserFromDetails(userDetails);
        User targetUser = targetUsername != null && !targetUsername.equals(currentUser.getUsername())
                ? userRepository.findByUsername(targetUsername)
                        .orElseThrow(() -> new EntityNotFoundException("Target user not found: " + targetUsername))
                : currentUser;

        // 4. Check if already copied
        if (pipelineCopyRepository.existsBySourceTrainingAndCopyForUserAndStatus(
                sourceTraining, targetUser, PipelineCopyStatusEnum.COMPLETED)) {
            throw new IllegalStateException("Pipeline already copied to this user");
        }

        // 5. Create PipelineCopy record
        PipelineCopy pipelineCopy = createPipelineCopyRecord(sourceTraining, currentUser, targetUser);
        recordHistory(pipelineCopy, PipelineCopyActionTypeEnum.COPY_INITIATED, currentUser, "Pipeline copy initiated");

        List<String> copiedMinioKeys = new ArrayList<>();

        try {
            // 6. Copy Dataset + MinIO file
            Dataset newDataset = copyDataset(sourceTraining.getDatasetConfiguration().getDataset(),
                    targetUser, pipelineCopy, copiedMinioKeys);

            // 7. Copy DatasetConfiguration
            DatasetConfiguration newDatasetConfig = copyDatasetConfiguration(
                    sourceTraining.getDatasetConfiguration(), newDataset, pipelineCopy);

            // 8. Copy AlgorithmConfiguration OR CustomAlgorithmConfiguration
            AlgorithmConfiguration newAlgoConfig = null;
            CustomAlgorithmConfiguration newCustomAlgoConfig = null;
            if (sourceTraining.getAlgorithmConfiguration() != null) {
                newAlgoConfig = copyAlgorithmConfiguration(
                        sourceTraining.getAlgorithmConfiguration(), targetUser, pipelineCopy);
            }
            if (sourceTraining.getCustomAlgorithmConfiguration() != null) {
                newCustomAlgoConfig = copyCustomAlgorithmConfiguration(
                        sourceTraining.getCustomAlgorithmConfiguration(), targetUser, pipelineCopy);
            }

            // 9. Create new Training
            Training newTraining = copyTraining(sourceTraining, targetUser, newDatasetConfig,
                    newAlgoConfig, newCustomAlgoConfig, pipelineCopy);

            // 10. Copy Model + MinIO artifacts
            Model newModel = null;
            if (sourceTraining.getModel() != null) {
                newModel = copyModel(sourceTraining.getModel(), newTraining, pipelineCopy, copiedMinioKeys);
                newTraining.setModel(newModel);
                trainingRepository.save(newTraining);
            }

            // 11. Update PipelineCopy as completed
            pipelineCopy.setTargetTraining(newTraining);
            pipelineCopy.setStatus(PipelineCopyStatusEnum.COMPLETED);
            pipelineCopyRepository.save(pipelineCopy);

            // 12. Record history
            recordHistory(pipelineCopy, PipelineCopyActionTypeEnum.COPY_COMPLETED, currentUser, null);

            log.info("Pipeline copy completed: {} -> {} for user {}",
                    trainingId, newTraining.getId(), targetUser.getUsername());

            return buildResponse(pipelineCopy);

        } catch (Exception e) {
            log.error("Pipeline copy failed for training {}: {}", trainingId, e.getMessage(), e);
            rollbackPipelineCopy(pipelineCopy, currentUser, copiedMinioKeys, e);
            throw new RuntimeException("Pipeline copy failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PipelineCopyResponse> copyPipelineToGroup(Integer trainingId, Integer groupId, UserDetails userDetails) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group not found with id: " + groupId));

        // Validate that the user is the group leader or admin
        User currentUser = getUserFromDetails(userDetails);
        if (!group.getLeader().getId().equals(currentUser.getId()) && !authorizationHelper.isAdmin(userDetails)) {
            throw new AccessDeniedException("Only the group leader or admin can copy pipelines to group members");
        }

        Set<User> allMembers = groupService.getAllGroupMembers(groupId);
        List<PipelineCopyResponse> responses = new ArrayList<>();

        for (User member : allMembers) {
            if (!member.getId().equals(currentUser.getId())) {
                try {
                    PipelineCopyResponse response = copyPipeline(trainingId, member.getUsername(), userDetails);
                    responses.add(response);
                } catch (IllegalStateException e) {
                    // Already copied to this user, skip
                    log.info("Pipeline already copied to user {}, skipping", member.getUsername());
                } catch (Exception e) {
                    log.error("Failed to copy pipeline to user {}: {}", member.getUsername(), e.getMessage());
                }
            }
        }

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public PipelineCopy getPipelineCopyWithMappings(Integer pipelineCopyId) {
        return pipelineCopyRepository.findByIdWithMappings(pipelineCopyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PipelineCopy> getCopiesInitiatedByUser(UserDetails userDetails) {
        User user = getUserFromDetails(userDetails);
        return pipelineCopyRepository.findByCopiedByUserIdOrderByCopyDateDesc(user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PipelineCopy> getCopiesReceivedByUser(UserDetails userDetails) {
        User user = getUserFromDetails(userDetails);
        return pipelineCopyRepository.findByCopyForUserIdOrderByCopyDateDesc(user.getId());
    }

    // ========== Private Helper Methods ==========

    private PipelineCopy createPipelineCopyRecord(Training sourceTraining, User copiedByUser, User copyForUser) {
        PipelineCopy pipelineCopy = PipelineCopy.builder()
                .sourceTraining(sourceTraining)
                .copiedByUser(copiedByUser)
                .copyForUser(copyForUser)
                .copyDate(ZonedDateTime.now())
                .status(PipelineCopyStatusEnum.IN_PROGRESS)
                .mappings(new ArrayList<>())
                .build();
        return pipelineCopyRepository.save(pipelineCopy);
    }

    private Dataset copyDataset(Dataset source, User targetUser, PipelineCopy pipelineCopy, List<String> copiedMinioKeys) {
        String datasetBucket = bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET);
        String sourceKey = minioService.extractMinioKey(source.getFilePath());
        String targetKey = generateCopyKey(sourceKey, targetUser.getUsername());

        // Copy file in MinIO
        minioService.copyObject(datasetBucket, sourceKey, datasetBucket, targetKey);
        copiedMinioKeys.add(datasetBucket + "/" + targetKey);

        // Create new Dataset entity
        Dataset newDataset = new Dataset();
        newDataset.setUser(targetUser);
        newDataset.setOriginalFileName(source.getOriginalFileName());
        newDataset.setFileName("COPY_" + System.currentTimeMillis() + "_" + source.getFileName());
        newDataset.setFilePath(datasetBucket + "/" + targetKey);
        newDataset.setFileSize(source.getFileSize());
        newDataset.setContentType(source.getContentType());
        newDataset.setUploadDate(ZonedDateTime.now());
        newDataset.setAccessibility(source.getAccessibility());
        newDataset.setCategory(source.getCategory());
        newDataset.setDescription("Pipeline copy from dataset ID " + source.getId());

        Dataset savedDataset = datasetRepository.save(newDataset);

        // Record mapping
        addMapping(pipelineCopy, PipelineEntityTypeEnum.DATASET,
                source.getId(), savedDataset.getId(), sourceKey, targetKey);

        return savedDataset;
    }

    private DatasetConfiguration copyDatasetConfiguration(DatasetConfiguration source, Dataset newDataset, PipelineCopy pipelineCopy) {
        DatasetConfiguration newConfig = DatasetConfiguration.builder()
                .basicAttributesColumns(source.getBasicAttributesColumns())
                .targetColumn(source.getTargetColumn())
                .uploadDate(ZonedDateTime.now())
                .status(source.getStatus())
                .dataset(newDataset)
                .build();

        DatasetConfiguration savedConfig = datasetConfigurationRepository.save(newConfig);

        addMapping(pipelineCopy, PipelineEntityTypeEnum.DATASET_CONFIG,
                source.getId(), savedConfig.getId(), null, null);

        return savedConfig;
    }

    private AlgorithmConfiguration copyAlgorithmConfiguration(AlgorithmConfiguration source, User targetUser, PipelineCopy pipelineCopy) {
        AlgorithmConfiguration newConfig = AlgorithmConfiguration.builder()
                .algorithm(source.getAlgorithm())
                .options(source.getOptions())
                .algorithmType(source.getAlgorithmType())
                .user(targetUser)
                .build();

        AlgorithmConfiguration savedConfig = algorithmConfigurationRepository.save(newConfig);

        addMapping(pipelineCopy, PipelineEntityTypeEnum.ALGORITHM_CONFIG,
                source.getId(), savedConfig.getId(), null, null);

        return savedConfig;
    }

    private CustomAlgorithmConfiguration copyCustomAlgorithmConfiguration(CustomAlgorithmConfiguration source, User targetUser, PipelineCopy pipelineCopy) {
        CustomAlgorithmConfiguration newConfig = new CustomAlgorithmConfiguration();
        newConfig.setAlgorithm(source.getAlgorithm());
        newConfig.setUser(targetUser);
        // Note: Parameters are not copied as they reference the original configuration

        CustomAlgorithmConfiguration savedConfig = customAlgorithmConfigurationRepository.save(newConfig);

        addMapping(pipelineCopy, PipelineEntityTypeEnum.CUSTOM_ALGORITHM_CONFIG,
                source.getId(), savedConfig.getId(), null, null);

        return savedConfig;
    }

    private Training copyTraining(Training source, User targetUser, DatasetConfiguration newDatasetConfig,
                                  AlgorithmConfiguration newAlgoConfig, CustomAlgorithmConfiguration newCustomAlgoConfig,
                                  PipelineCopy pipelineCopy) {
        Training newTraining = Training.builder()
                .startedDate(source.getStartedDate())
                .finishedDate(source.getFinishedDate())
                .status(source.getStatus())
                .algorithmConfiguration(newAlgoConfig)
                .customAlgorithmConfiguration(newCustomAlgoConfig)
                .user(targetUser)
                .datasetConfiguration(newDatasetConfig)
                .results(source.getResults())
                .build();

        Training savedTraining = trainingRepository.save(newTraining);

        addMapping(pipelineCopy, PipelineEntityTypeEnum.TRAINING,
                source.getId(), savedTraining.getId(), null, null);

        return savedTraining;
    }

    private Model copyModel(Model source, Training newTraining, PipelineCopy pipelineCopy, List<String> copiedMinioKeys) {
        String modelBucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
        String metricsBucket = bucketResolver.resolve(BucketTypeEnum.METRICS);

        // Copy model file
        String modelTargetKey = null;
        if (source.getModelUrl() != null && !source.getModelUrl().isBlank()) {
            String modelSourceKey = minioService.extractMinioKey(source.getModelUrl());
            modelTargetKey = generateCopyKey(modelSourceKey, newTraining.getUser().getUsername());
            minioService.copyObject(modelBucket, modelSourceKey, modelBucket, modelTargetKey);
            copiedMinioKeys.add(modelBucket + "/" + modelTargetKey);
        }

        // Copy metrics file
        String metricsTargetKey = null;
        if (source.getMetricsUrl() != null && !source.getMetricsUrl().isBlank()) {
            String metricsSourceKey = minioService.extractMinioKey(source.getMetricsUrl());
            metricsTargetKey = generateCopyKey(metricsSourceKey, newTraining.getUser().getUsername());
            minioService.copyObject(metricsBucket, metricsSourceKey, metricsBucket, metricsTargetKey);
            copiedMinioKeys.add(metricsBucket + "/" + metricsTargetKey);
        }

        // Copy label mapping file
        String labelMappingTargetKey = null;
        if (source.getLabelMappingUrl() != null && !source.getLabelMappingUrl().isBlank()) {
            String labelMappingSourceKey = minioService.extractMinioKey(source.getLabelMappingUrl());
            labelMappingTargetKey = generateCopyKey(labelMappingSourceKey, newTraining.getUser().getUsername());
            minioService.copyObject(modelBucket, labelMappingSourceKey, modelBucket, labelMappingTargetKey);
            copiedMinioKeys.add(modelBucket + "/" + labelMappingTargetKey);
        }

        // Copy feature columns file
        String featureColumnsTargetKey = null;
        if (source.getFeatureColumnsUrl() != null && !source.getFeatureColumnsUrl().isBlank()) {
            String featureColumnsSourceKey = minioService.extractMinioKey(source.getFeatureColumnsUrl());
            featureColumnsTargetKey = generateCopyKey(featureColumnsSourceKey, newTraining.getUser().getUsername());
            minioService.copyObject(modelBucket, featureColumnsSourceKey, modelBucket, featureColumnsTargetKey);
            copiedMinioKeys.add(modelBucket + "/" + featureColumnsTargetKey);
        }

        // Create new Model entity
        Model newModel = Model.builder()
                .training(newTraining)
                .modelUrl(modelTargetKey != null ? modelBucket + "/" + modelTargetKey : null)
                .modelType(source.getModelType())
                .status(source.getStatus())
                .accessibility(source.getAccessibility())
                .name("Copy of " + (source.getName() != null ? source.getName() : "Model " + source.getId()))
                .description(source.getDescription())
                .dataDescription(source.getDataDescription())
                .keywords(source.getKeywords() != null ? new ArrayList<>(source.getKeywords()) : null)
                .createdAt(ZonedDateTime.now())
                .finalized(source.isFinalized())
                .finalizationDate(source.isFinalized() ? ZonedDateTime.now() : null)
                .category(source.getCategory())
                .metricsUrl(metricsTargetKey != null ? metricsBucket + "/" + metricsTargetKey : null)
                .labelMappingUrl(labelMappingTargetKey != null ? modelBucket + "/" + labelMappingTargetKey : null)
                .featureColumnsUrl(featureColumnsTargetKey != null ? modelBucket + "/" + featureColumnsTargetKey : null)
                .build();

        Model savedModel = modelRepository.save(newModel);

        String modelSourceKey = source.getModelUrl() != null ? minioService.extractMinioKey(source.getModelUrl()) : null;
        addMapping(pipelineCopy, PipelineEntityTypeEnum.MODEL,
                source.getId(), savedModel.getId(), modelSourceKey, modelTargetKey);

        return savedModel;
    }

    private void addMapping(PipelineCopy pipelineCopy, PipelineEntityTypeEnum entityType,
                            Integer sourceId, Integer targetId, String sourceKey, String targetKey) {
        PipelineCopyMapping mapping = PipelineCopyMapping.builder()
                .pipelineCopy(pipelineCopy)
                .entityType(entityType)
                .sourceEntityId(sourceId)
                .targetEntityId(targetId)
                .minioSourceKey(sourceKey)
                .minioTargetKey(targetKey)
                .status(PipelineCopyMappingStatusEnum.COPIED)
                .build();
        pipelineCopy.addMapping(mapping);
        pipelineCopyMappingRepository.save(mapping);
    }

    private void recordHistory(PipelineCopy pipelineCopy, PipelineCopyActionTypeEnum actionType, User user, String details) {
        PipelineCopyActionType action = actionTypeRepository.findByName(actionType)
                .orElseThrow(() -> new EntityNotFoundException("Action type not found: " + actionType));

        PipelineCopyHistory history = PipelineCopyHistory.builder()
                .pipelineCopy(pipelineCopy)
                .actionType(action)
                .actionByUser(user)
                .actionAt(ZonedDateTime.now())
                .details(details)
                .build();
        pipelineCopyHistoryRepository.save(history);
    }

    private void rollbackPipelineCopy(PipelineCopy pipelineCopy, User user, List<String> copiedMinioKeys, Exception e) {
        log.warn("Rolling back pipeline copy {}: {}", pipelineCopy.getId(), e.getMessage());

        // Delete copied MinIO files
        for (String key : copiedMinioKeys) {
            try {
                String[] parts = key.split("/", 2);
                if (parts.length == 2) {
                    minioService.deleteObject(parts[0], parts[1]);
                }
            } catch (Exception deleteEx) {
                log.warn("Failed to delete MinIO object during rollback: {}", deleteEx.getMessage());
            }
        }

        // Update mappings as failed
        for (PipelineCopyMapping mapping : pipelineCopy.getMappings()) {
            mapping.setStatus(PipelineCopyMappingStatusEnum.FAILED);
            mapping.setErrorMessage("Rolled back: " + e.getMessage());
        }

        // Update PipelineCopy status
        pipelineCopy.setStatus(PipelineCopyStatusEnum.ROLLED_BACK);
        pipelineCopy.setErrorMessage(e.getMessage());
        pipelineCopyRepository.save(pipelineCopy);

        // Record rollback in history
        recordHistory(pipelineCopy, PipelineCopyActionTypeEnum.COPY_ROLLED_BACK, user, e.getMessage());
    }

    private void validateCopyPermission(Training training, UserDetails userDetails) {
        User user = getUserFromDetails(userDetails);

        // Admin/Manager can copy anything
        if (authorizationHelper.isSuperModelUser(userDetails)) {
            return;
        }

        // Owner can copy
        if (training.getUser().getId().equals(user.getId())) {
            return;
        }

        // Check if model is shared with user
        Model model = training.getModel();
        if (model != null && modelShareService.hasAccessToModel(model.getId(), user)) {
            return;
        }

        throw new AccessDeniedException("Not authorized to copy this pipeline");
    }

    private User getUserFromDetails(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userDetails.getUsername()));
    }

    private String generateCopyKey(String originalKey, String username) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        int lastSlash = originalKey.lastIndexOf('/');
        String directory = lastSlash > 0 ? originalKey.substring(0, lastSlash) : "";
        String filename = lastSlash > 0 ? originalKey.substring(lastSlash + 1) : originalKey;

        String newFilename = "COPY_" + timestamp + "_" + username + "_" + filename;
        return directory.isEmpty() ? newFilename : directory + "/" + newFilename;
    }

    private PipelineCopyResponse buildResponse(PipelineCopy pipelineCopy) {
        Map<String, PipelineCopyResponse.EntityMapping> mappings = pipelineCopy.getMappings().stream()
                .collect(Collectors.toMap(
                        m -> m.getEntityType().name(),
                        m -> PipelineCopyResponse.EntityMapping.builder()
                                .sourceId(m.getSourceEntityId())
                                .targetId(m.getTargetEntityId())
                                .minioSourceKey(m.getMinioSourceKey())
                                .minioTargetKey(m.getMinioTargetKey())
                                .status(m.getStatus().name())
                                .build()
                ));

        return PipelineCopyResponse.builder()
                .pipelineCopyId(pipelineCopy.getId())
                .sourceTrainingId(pipelineCopy.getSourceTraining().getId())
                .targetTrainingId(pipelineCopy.getTargetTraining() != null ? pipelineCopy.getTargetTraining().getId() : null)
                .copiedByUsername(pipelineCopy.getCopiedByUser().getUsername())
                .copyForUsername(pipelineCopy.getCopyForUser().getUsername())
                .copyDate(pipelineCopy.getCopyDate())
                .status(pipelineCopy.getStatus())
                .errorMessage(pipelineCopy.getErrorMessage())
                .mappings(mappings)
                .build();
    }
}
