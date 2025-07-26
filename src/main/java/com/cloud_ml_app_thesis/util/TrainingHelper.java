package com.cloud_ml_app_thesis.util;

import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.dto.request.train.TrainingStartRequest;
import com.cloud_ml_app_thesis.dto.response.*;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.DatasetFunctionalTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.service.DatasetService;
import com.cloud_ml_app_thesis.service.MinioService;
import jakarta.persistence.EntityNotFoundException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import weka.core.Instances;

import java.io.InputStream;
import java.time.ZonedDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrainingHelper {

    private final TrainingRepository trainingRepository;
    private final ModelRepository modelRepository;

    private final AlgorithmConfigurationRepository algorithmConfigurationRepository;

    private final AlgorithmRepository algorithmRepository;

    private final DatasetRepository datasetRepository;

    private final DatasetConfigurationRepository datasetConfigurationRepository;

    private final DatasetService datasetService;

    private final MinioService minioService;

    private final TrainingStatusRepository trainingStatusRepository;

    public TrainingDataInput configureTrainingDataInputByTrainCase(TrainingStartRequest request, User user) throws Exception {
        log.info("🔧 Configuring training input for user: {}", user.getUsername());

        // Extract & validate input values
        MultipartFile file = request.getFile();
        boolean hasFile = ValidationUtil.multipartFileExist(file);
        boolean hasDatasetId = ValidationUtil.stringExists(request.getDatasetId());
        boolean hasDatasetConfId = ValidationUtil.stringExists(request.getDatasetConfigurationId());
        boolean hasAlgorithmId = ValidationUtil.stringExists(request.getAlgorithmId());
        boolean hasAlgorithmConfId = ValidationUtil.stringExists(request.getAlgorithmConfigurationId());
        boolean hasBasicCols = ValidationUtil.stringExists(request.getBasicCharacteristicsColumns());
        boolean hasTargetCol = ValidationUtil.stringExists(request.getTargetClassColumn());
        boolean hasAlgorithmOpts = ValidationUtil.stringExists(request.getOptions());
        boolean hasTrainingId = ValidationUtil.stringExists(request.getTrainingId());
        boolean hasModelId = ValidationUtil.stringExists(request.getModelId());

        // Conflict checks
        if (hasTrainingId && hasModelId)
            return error("❌ You can't train based on both a training and a model.");

        if (hasDatasetId && hasFile)
            return error("❌ Provide either an uploaded dataset OR a new file, not both.");

        if (hasDatasetId && hasDatasetConfId)
            return error("❌ Cannot use both datasetId and datasetConfigurationId.");

        if (hasDatasetConfId && hasBasicCols && hasTargetCol)
            return error("❌ When using a datasetConfigurationId, omit basicCharacteristicsColumns and targetClassColumn.");

        if (hasAlgorithmId && hasAlgorithmConfId)
            return error("❌ Cannot use both algorithmId and algorithmConfigurationId.");

        if (hasAlgorithmConfId && hasAlgorithmOpts)
            return error("❌ Cannot pass algorithmOptions if algorithmConfigurationId is used.");

        if (hasAlgorithmConfId && hasDatasetConfId && hasTargetCol && hasBasicCols && hasAlgorithmOpts)
            return error("❌ This looks like a full new training; do not use retrain mode. Start fresh.");

        // Upload file if needed
        String datasetId;
        if (hasFile) {
            GenericResponse<Dataset> uploadResp = datasetService.uploadDataset(file, user, DatasetFunctionalTypeEnum.TRAIN);
            datasetId = uploadResp.getDataHeader().getId().toString();
            hasDatasetId = true;
            log.info("📂 File uploaded, datasetId = {}", datasetId);
        } else {
            datasetId = request.getDatasetId();
        }

        // Dataset configuration logic
        Instances datasetInstances = null;
        DatasetConfiguration datasetConf;

        Algorithm algorithm = algorithmRepository.findById(Integer.parseInt(request.getAlgorithmId()))
                .orElseThrow(() -> new EntityNotFoundException("Algorithm not found."));

        //If Classification
        if(algorithm.getType().getName().equals(AlgorithmTypeEnum.CLASSIFICATION)) {
            if (hasDatasetId) {
                log.info("Inside datasetId: {}", datasetId);
                Dataset dataset = datasetRepository.findById(Integer.parseInt(datasetId))
                        .orElseThrow(() -> new EntityNotFoundException("Dataset not found: id=" + datasetId));
                datasetConf = new DatasetConfiguration();
                datasetConf.setDataset(dataset);
                if (hasBasicCols) datasetConf.setBasicAttributesColumns(request.getBasicCharacteristicsColumns());
                if (hasTargetCol) datasetConf.setTargetColumn(request.getTargetClassColumn());
                datasetConf = datasetConfigurationRepository.save(datasetConf);

                if(hasFile){
                    datasetInstances = DatasetUtil.prepareDataset(file, dataset.getFileName(), datasetConf);
                } else{
                    String[] minioInfoParts = DatasetUtil.resolveDatasetMinioInfo(datasetConf.getDataset());
                    InputStream datasetInputStream = minioService.loadObjectAsInputStream(minioInfoParts[0], minioInfoParts[1]);
                    datasetInstances = DatasetUtil.loadDatasetInstancesByDatasetConfigurationFromMinio(datasetConf, datasetInputStream, minioInfoParts[1]);
                }

            } else if (hasDatasetConfId) {
                log.info("Inside datasetConfId: {}", datasetId);
                 datasetConf = datasetConfigurationRepository.findById(Integer.parseInt(request.getDatasetConfigurationId()))
                        .orElseThrow(() -> new EntityNotFoundException("DatasetConfiguration not found."));
                if (hasBasicCols) datasetConf.setBasicAttributesColumns(request.getBasicCharacteristicsColumns());
                if (hasTargetCol) datasetConf.setTargetColumn(request.getTargetClassColumn());
                String[] minioInfoParts = DatasetUtil.resolveDatasetMinioInfo(datasetConf.getDataset());
                InputStream datasetInputStream = minioService.loadObjectAsInputStream(minioInfoParts[0], minioInfoParts[1]);
                datasetInstances = DatasetUtil.loadDatasetInstancesByDatasetConfigurationFromMinio(datasetConf, datasetInputStream, minioInfoParts[1]);
            } else {
                return error("❌ No valid dataset provided.");
            }
        }
        //If Clustering
        else{
            Dataset dataset = datasetRepository.findById(Integer.parseInt(datasetId))
                    .orElseThrow(() -> new EntityNotFoundException("Dataset not found: id=" + datasetId));
            datasetConf = new DatasetConfiguration();
            datasetConf.setDataset(dataset);
            if (hasTargetCol) datasetConf.setTargetColumn(request.getTargetClassColumn());
            if (hasBasicCols) datasetConf.setBasicAttributesColumns(request.getBasicCharacteristicsColumns());
            datasetConf = datasetConfigurationRepository.save(datasetConf);

            if(hasFile){
                datasetInstances = DatasetUtil.prepareDataset(file, dataset.getFileName(), datasetConf);
            } else{
                String[] minioInfoParts = DatasetUtil.resolveDatasetMinioInfo(datasetConf.getDataset());
                InputStream datasetInputStream = minioService.loadObjectAsInputStream(minioInfoParts[0], minioInfoParts[1]);
                datasetInstances = DatasetUtil.loadDatasetInstancesByDatasetConfigurationFromMinio(datasetConf, datasetInputStream, minioInfoParts[1]);
            }
            datasetConf.setUploadDate(ZonedDateTime.now());
            log.info("✅ Dataset instances loaded: {} instances", datasetInstances.numInstances());
        }

        // Algorithm configuration logic
        AlgorithmConfiguration algorithmConf = null;
        String rawOptions = null;
        if (hasAlgorithmId) {
            algorithmConf = new AlgorithmConfiguration(algorithm);
            algorithmConf.setUser(user);
            if (hasAlgorithmOpts) {
                // Ο χρήστης έστειλε κάτι (έστω και κενό string)
                log.info("📥 User provided options: '{}'", request.getOptions());
                rawOptions = request.getOptions();
            } else {
                // Ο χρήστης δεν έστειλε καθόλου πεδίο ➜ βάλε default
                log.info("⚙️ No user options provided, applying algorithm defaults: '{}'", algorithm.getDefaultOptions());
                rawOptions = algorithm.getDefaultOptions();
            }
            algorithmConf.setUser(user);
            algorithmConf.setOptions(rawOptions);
            algorithmConf = algorithmConfigurationRepository.save(algorithmConf);
        }
        // Handle training/model reuse
        Training training = new Training();
        if (hasTrainingId) {
            training = trainingRepository.findById(Integer.parseInt(request.getTrainingId()))
                    .orElseThrow(() -> new EntityNotFoundException("Training not found."));
            log.info("🔁 Reusing existing training [id={}]", training.getId());
        } else if (hasModelId) {
            Model model = modelRepository.findById(Integer.parseInt(request.getModelId()))
                    .orElseThrow(() -> new EntityNotFoundException("Model not found."));
            training = trainingRepository.findByModel(model)
                    .orElseThrow(() -> new EntityNotFoundException("Training not found for model."));
            log.info("🔁 Reusing training from model [id={}]", model.getId());
        }

        // Update reused training's config if needed
        if ((hasTrainingId || hasModelId) && !hasAlgorithmConfId && hasAlgorithmOpts) {
            algorithmConf = training.getAlgorithmConfiguration();
            algorithmConf.setOptions(request.getOptions());
            algorithmConf.setUser(user);
            algorithmConf = algorithmConfigurationRepository.save(algorithmConf);
        }

        if ((hasTrainingId || hasModelId) && !hasFile && !hasDatasetId && !hasDatasetConfId) {
            datasetConf = training.getDatasetConfiguration();
            if (hasBasicCols) datasetConf.setBasicAttributesColumns(request.getBasicCharacteristicsColumns());
            if (hasTargetCol) datasetConf.setTargetColumn(request.getTargetClassColumn());
        }

        TrainingStatus trainStatus = trainingStatusRepository.findByName(TrainingStatusEnum.REQUESTED).orElseThrow(() -> new EntityNotFoundException("Training status not found."));
        training.setUser(user);
        training.setStatus(trainStatus);
        training.setAlgorithmConfiguration(algorithmConf);
        training.setDatasetConfiguration(datasetConf);
        training = trainingRepository.save(training);

        return TrainingDataInput.builder()
                .training(training)
                .datasetConfiguration(datasetConf)
                .algorithmConfiguration(algorithmConf)
                .dataset(datasetInstances)
                .build();
    }

    private TrainingDataInput error(String message) {
        log.warn(message);
        return TrainingDataInput.builder()
                .errorResponse(new GenericResponse<>(null, "", message, new Metadata()))
                .build();
    }

}
