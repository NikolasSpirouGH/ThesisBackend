package com.cloud_ml_app_thesis.service.orchestrator;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.custom_algorithm.AlgorithmParameterDTO;
import com.cloud_ml_app_thesis.dto.request.training.CustomTrainRequest;
import com.cloud_ml_app_thesis.dto.train.CustomTrainMetadata;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.DatasetFunctionalTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskTypeEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.service.DatasetService;
import com.cloud_ml_app_thesis.service.MinioService;
import com.cloud_ml_app_thesis.util.AsyncManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrainingOrchestrator {

    private final TaskStatusRepository taskStatusRepository;
    private final UserRepository userRepository;
    private final DatasetService datasetService;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final BucketResolver bucketResolver;
    private final CustomAlgorithmRepository algorithmRepository;
    private final ModelMapper modelMapper;
    private final AsyncManager asyncManager;
    private final ObjectMapper objectMapper;
    private final MinioService minioService;

    public String handleCustomTrainingRequest(CustomTrainRequest request, AccountDetails accountDetails) {
        String taskId = UUID.randomUUID().toString();
        String username = accountDetails.getUsername();
        User user = accountDetails.getUser();

        taskStatusRepository.save(AsyncTaskStatus.builder()
                .taskId(taskId)
                .taskType(TaskTypeEnum.TRAINING)
                .status(TaskStatusEnum.PENDING)
                .startedAt(ZonedDateTime.now())
                .username(username)
                .build());

        Dataset dataset = datasetService.uploadDataset(
                request.getDatasetFile(), user, DatasetFunctionalTypeEnum.TRAIN
        ).getDataHeader();

        DatasetConfiguration config = new DatasetConfiguration();
        config.setDataset(dataset);
        config.setUploadDate(ZonedDateTime.now());
        config.setBasicAttributesColumns(request.getBasicAttributesColumns());
        config.setTargetColumn(request.getTargetColumn());
        config = datasetConfigurationRepository.save(config);

        CustomAlgorithm algorithm = algorithmRepository.findById(request.getAlgorithmId())
                .orElseThrow(() -> new IllegalArgumentException("Algorithm not found"));

        if (request.getParamsFile() != null && !request.getParamsFile().isEmpty()) {
            parseAndStoreParamsJson(request, algorithm, taskId);
        } else {

        }

        CustomTrainMetadata metadata = new CustomTrainMetadata(
                dataset.getFileName(),
                bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET),
                config.getId(),
                request.getAlgorithmId()
        );

        asyncManager.trainAsync(taskId, user, metadata);

        return taskId;
    }

    private void parseAndStoreParamsJson(CustomTrainRequest request, CustomAlgorithm algorithm, String taskId) {
        try (InputStream is = request.getParamsFile().getInputStream()) {
            List<AlgorithmParameterDTO> parameterDTOs =
                    objectMapper.readValue(is, new TypeReference<>() {
                    });
            List<AlgorithmParameter> parameters = parameterDTOs.stream()
                    .map(dto -> {
                        AlgorithmParameter param = modelMapper.map(dto, AlgorithmParameter.class);
                        param.setAlgorithm(algorithm);
                        return param;
                    }).toList();

            algorithm.getParameters().clear();
            algorithm.getParameters().addAll(parameters);

            log.info("Stored {} algorithm parameters for algorithm ID {}", parameters.size(), algorithm.getId());

        } catch (IOException e) {
            throw new FileProcessingException("Could not parse or upload params.json", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}