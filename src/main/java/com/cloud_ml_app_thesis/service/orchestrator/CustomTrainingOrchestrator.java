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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomTrainingOrchestrator {

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

        String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
        String paramsKey = null;
        String paramsBucket = null;
        log.info("▶ parametersFile: {}", request.getParametersFile());
        log.info("▶ isEmpty: {}", request.getParametersFile() != null ? request.getParametersFile().isEmpty() : "null");

        if (request.getParametersFile() != null && !request.getParametersFile().isEmpty()) {
            paramsKey = user.getUsername() +  "_" + timestamp + "_" + request.getParametersFile().getOriginalFilename();
            paramsBucket = bucketResolver.resolve(BucketTypeEnum.PARAMETERS);
            try{
                minioService.uploadObjectToBucket(request.getParametersFile(), paramsBucket, paramsKey);
            }catch(IOException e){
                throw new FileProcessingException("Failed to upload parameters file to PARAMETERS bucket", e);
            }
            log.info("✅ Uploaded parameters file to MinIO: {}/{}", paramsBucket, paramsKey);
        }else {
            log.info("📭 No params.json provided. Will use default algorithm parameters.");
        }

        CustomTrainMetadata metadata = new CustomTrainMetadata(
                dataset.getFileName(),
                bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET),
                config.getId(),
                request.getAlgorithmId(),
                paramsKey,
                paramsBucket
        );

        asyncManager.trainAsync(taskId, user, metadata);

        return taskId;
    }
}