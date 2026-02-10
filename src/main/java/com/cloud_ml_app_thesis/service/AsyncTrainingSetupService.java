package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.request.train.TrainingStartRequest;
import com.cloud_ml_app_thesis.dto.train.CustomTrainMetadata;
import com.cloud_ml_app_thesis.dto.train.DeferredCustomTrainInput;
import com.cloud_ml_app_thesis.dto.train.DeferredPredictionInput;
import com.cloud_ml_app_thesis.dto.train.DeferredWekaTrainInput;
import com.cloud_ml_app_thesis.dto.train.WekaContainerTrainMetadata;
import com.cloud_ml_app_thesis.entity.CustomAlgorithm;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.entity.TrainingDataInput;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.DatasetFunctionalTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.CustomAlgorithmRepository;
import com.cloud_ml_app_thesis.repository.DatasetConfigurationRepository;
import com.cloud_ml_app_thesis.repository.TrainingRepository;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.util.PathBackedMultipartFile;
import com.cloud_ml_app_thesis.util.TrainingHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service that encapsulates the "heavy setup" logic extracted from orchestrators.
 * This is NOT @Async - it performs MinIO uploads and entity creation synchronously,
 * but is called from @Async methods in AsyncManager.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncTrainingSetupService {

    private final DatasetService datasetService;
    private final MinioService minioService;
    private final BucketResolver bucketResolver;
    private final TrainingHelper trainingHelper;
    private final TrainingRepository trainingRepository;
    private final TrainingStatusRepository trainingStatusRepository;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final DatasetRepository datasetRepository;
    private final CustomAlgorithmRepository customAlgorithmRepository;
    private final ModelRepository modelRepository;

    /**
     * Prepares custom training by uploading dataset to MinIO and creating entities.
     * Contains logic extracted from TrainingOrchestrator lines 121-199.
     */
    public CustomTrainMetadata prepareCustomTraining(User user, DeferredCustomTrainInput input) {
        log.info("📦 [SETUP] Preparing custom training for user={}", user.getUsername());

        // Find base training for retrain mode
        Training retrainedFrom = null;
        boolean retrainMode = input.retrainFromTrainingId() != null || input.retrainFromModelId() != null;

        if (input.retrainFromTrainingId() != null) {
            retrainedFrom = trainingRepository.findById(input.retrainFromTrainingId())
                    .orElseThrow(() -> new EntityNotFoundException("Training not found: " + input.retrainFromTrainingId()));
            log.info("🔁 Re-training custom model from trainingId={}", retrainedFrom.getId());
        } else if (input.retrainFromModelId() != null) {
            var model = modelRepository.findById(input.retrainFromModelId())
                    .orElseThrow(() -> new EntityNotFoundException("Model not found: " + input.retrainFromModelId()));
            retrainedFrom = trainingRepository.findByModel(model)
                    .orElseThrow(() -> new EntityNotFoundException("Training not found for model: " + input.retrainFromModelId()));
            log.info("🔁 Re-training custom model from modelId={}, trainingId={}", model.getId(), retrainedFrom.getId());
        }

        // Resolve algorithm
        CustomAlgorithm algorithm;
        if (input.algorithmId() != null) {
            algorithm = customAlgorithmRepository.findWithOwnerById(input.algorithmId())
                    .orElseThrow(() -> new IllegalArgumentException("Algorithm not found: " + input.algorithmId()));
        } else if (retrainMode && retrainedFrom != null) {
            algorithm = retrainedFrom.getCustomAlgorithmConfiguration().getAlgorithm();
            log.info("📋 Using algorithm from base training: {}", algorithm.getName());
        } else {
            throw new BadRequestException("algorithmId is required for new training.");
        }

        // Resolve dataset
        Dataset dataset;
        if (input.existingDatasetId() != null) {
            // Use existing dataset from database
            dataset = datasetRepository.findById(input.existingDatasetId())
                    .orElseThrow(() -> new EntityNotFoundException("Dataset not found: " + input.existingDatasetId()));
            log.info("📂 Using existing dataset: id={}, fileName={}", dataset.getId(), dataset.getFileName());
        } else if (input.datasetTempFile() != null) {
            // Create PathBackedMultipartFile and upload
            MultipartFile pbmf = new PathBackedMultipartFile(
                    input.datasetTempFile(),
                    input.datasetOriginalFilename(),
                    input.datasetContentType(),
                    input.datasetFileSize()
            );
            dataset = datasetService.uploadDataset(pbmf, user, DatasetFunctionalTypeEnum.TRAIN, null).getDataHeader();
            log.info("📂 New dataset uploaded: {}", dataset.getFileName());
        } else if (retrainMode && retrainedFrom != null) {
            DatasetConfiguration baseDatasetConfig = retrainedFrom.getDatasetConfiguration();
            if (baseDatasetConfig == null || baseDatasetConfig.getDataset() == null) {
                throw new BadRequestException("Base training has no dataset configuration.");
            }
            dataset = baseDatasetConfig.getDataset();
            log.info("📋 Using dataset from base training: {}", dataset.getFileName());
        } else {
            throw new BadRequestException("datasetFile or datasetId is required for new training.");
        }

        // Resolve dataset configuration
        DatasetConfiguration datasetConfig = new DatasetConfiguration();
        datasetConfig.setDataset(dataset);
        datasetConfig.setUploadDate(ZonedDateTime.now());

        if (input.basicAttributesColumns() != null) {
            datasetConfig.setBasicAttributesColumns(input.basicAttributesColumns());
        } else if (retrainMode && retrainedFrom != null && retrainedFrom.getDatasetConfiguration() != null) {
            datasetConfig.setBasicAttributesColumns(retrainedFrom.getDatasetConfiguration().getBasicAttributesColumns());
        }

        if (input.targetColumn() != null) {
            datasetConfig.setTargetColumn(input.targetColumn());
        } else if (retrainMode && retrainedFrom != null && retrainedFrom.getDatasetConfiguration() != null) {
            datasetConfig.setTargetColumn(retrainedFrom.getDatasetConfiguration().getTargetColumn());
        }

        datasetConfig = datasetConfigurationRepository.save(datasetConfig);

        // Create Training entity with retrain linkage
        Training training = new Training();
        training.setUser(user);
        training.setDatasetConfiguration(datasetConfig);
        training.setRetrainedFrom(retrainedFrom);
        training.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.REQUESTED)
                .orElseThrow(() -> new EntityNotFoundException("TrainingStatus REQUESTED not found")));
        training = trainingRepository.save(training);

        log.info("✅ Custom training created: trainingId={}, algorithmId={}, retrainedFrom={}",
                training.getId(), algorithm.getId(), retrainedFrom != null ? retrainedFrom.getId() : "none");

        // Handle parameters file
        String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
        String paramsKey = null;
        String paramsBucket = null;

        if (input.paramsTempFile() != null) {
            MultipartFile paramsPbmf = new PathBackedMultipartFile(
                    input.paramsTempFile(),
                    input.paramsOriginalFilename(),
                    "application/json",
                    input.paramsTempFile().toFile().length()
            );
            paramsKey = user.getUsername() + "_" + timestamp + "_" + input.paramsOriginalFilename();
            paramsBucket = bucketResolver.resolve(BucketTypeEnum.PARAMETERS);
            try {
                minioService.uploadObjectToBucket(paramsPbmf, paramsBucket, paramsKey);
            } catch (IOException e) {
                throw new FileProcessingException("Failed to upload parameters file to PARAMETERS bucket", e);
            }
            log.info("✅ Uploaded parameters file to MinIO: {}/{}", paramsBucket, paramsKey);
        } else {
            log.info("📭 No params.json provided. Will use default algorithm parameters.");
        }

        return new CustomTrainMetadata(
                dataset.getFileName(),
                bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET),
                datasetConfig.getId(),
                algorithm.getId(),
                paramsKey,
                paramsBucket,
                training.getId()
        );
    }

    /**
     * Prepares Weka training by uploading dataset to MinIO and creating entities.
     * Contains logic extracted from TrainingOrchestrator lines 214-236.
     */
    public WekaContainerTrainMetadata prepareWekaTraining(User user, DeferredWekaTrainInput input) throws Exception {
        log.info("📦 [SETUP] Preparing Weka training for user={}", user.getUsername());

        // Reconstruct TrainingStartRequest with PathBackedMultipartFile
        TrainingStartRequest request = new TrainingStartRequest();

        if (input.datasetTempFile() != null) {
            MultipartFile pbmf = new PathBackedMultipartFile(
                    input.datasetTempFile(),
                    input.datasetOriginalFilename(),
                    input.datasetContentType(),
                    input.datasetFileSize()
            );
            request.setFile(pbmf);
        }

        request.setAlgorithmId(input.algorithmId());
        request.setAlgorithmConfigurationId(input.algorithmConfigurationId());
        request.setDatasetId(input.datasetId());
        request.setDatasetConfigurationId(input.datasetConfigurationId());
        request.setBasicCharacteristicsColumns(input.basicCharacteristicsColumns());
        request.setTargetClassColumn(input.targetClassColumn());
        request.setOptions(input.options());
        request.setTrainingId(input.trainingId());
        request.setModelId(input.modelId());

        // Use TrainingHelper to configure training (creates Training, DatasetConfiguration, AlgorithmConfiguration)
        TrainingDataInput trainingInput = trainingHelper.configureTrainingDataInputByTrainCase(request, user);

        if (trainingInput.getErrorResponse() != null) {
            throw new BadRequestException(trainingInput.getErrorResponse().getMessage());
        }

        DatasetConfiguration datasetConfig = trainingInput.getDatasetConfiguration();
        String datasetBucket = bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET);
        String datasetKey = datasetConfig.getDataset().getFileName();

        return new WekaContainerTrainMetadata(
                trainingInput.getTraining().getId(),
                datasetConfig.getId(),
                trainingInput.getAlgorithmConfiguration().getId(),
                datasetBucket,
                datasetKey,
                datasetConfig.getTargetColumn(),
                datasetConfig.getBasicAttributesColumns()
        );
    }

    /**
     * Prepares prediction by uploading the prediction file to MinIO or using existing dataset.
     * Contains logic extracted from PredictionOrchestrator line 50.
     *
     * @return Object array: [datasetKey (String), useTrainBucket (Boolean)]
     */
    public Object[] preparePrediction(User user, DeferredPredictionInput input) {
        log.info("📦 [SETUP] Preparing prediction for user={}", user.getUsername());

        String datasetKey;
        boolean useTrainBucket;

        if (input.existingDatasetId() != null) {
            // Use existing dataset - determine bucket based on functional type
            Dataset dataset = datasetRepository.findById(input.existingDatasetId())
                    .orElseThrow(() -> new EntityNotFoundException("Dataset not found: " + input.existingDatasetId()));
            datasetKey = dataset.getFileName();
            // TRAIN datasets are in TRAIN_DATASET bucket, PREDICT datasets are in PREDICT_DATASET bucket
            // Default to TRAIN bucket if functionalType is null (for older datasets)
            useTrainBucket = dataset.getFunctionalType() == null || dataset.getFunctionalType() == DatasetFunctionalTypeEnum.TRAIN;
            log.info("📂 Using existing dataset for prediction: id={}, fileName={}, functionalType={}, useTrainBucket={}",
                    dataset.getId(), datasetKey, dataset.getFunctionalType(), useTrainBucket);
        } else if (input.predictionTempFile() != null) {
            // Upload new prediction file to PREDICT_DATASET bucket AND save as Dataset entity
            MultipartFile pbmf = new PathBackedMultipartFile(
                    input.predictionTempFile(),
                    input.originalFilename(),
                    input.contentType(),
                    input.fileSize()
            );
            // Save as Dataset entity (with PREDICT functional type, PRIVATE accessibility)
            Dataset dataset = datasetService.uploadDataset(pbmf, user, DatasetFunctionalTypeEnum.PREDICT, null).getDataHeader();
            datasetKey = dataset.getFileName();
            useTrainBucket = false;
            log.info("📤 Uploaded prediction file as dataset: id={}, fileName={}", dataset.getId(), datasetKey);
        } else {
            throw new BadRequestException("Either predictionFile or datasetId is required for prediction.");
        }

        return new Object[]{datasetKey, useTrainBucket};
    }
}
