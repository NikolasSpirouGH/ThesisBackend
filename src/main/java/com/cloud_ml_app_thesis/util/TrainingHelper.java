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
        final boolean retrainMode = hasTrainingId || hasModelId; // ✅ NEW

        // ----- Conflicts (κρατάω τα υφιστάμενα, με 2 μικρές αλλαγές)
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

        // ΜΗ μπλοκάρεις "full new training" όταν είμαστε σε fresh mode.
        if (retrainMode && hasAlgorithmConfId && hasDatasetConfId && hasTargetCol && hasBasicCols && hasAlgorithmOpts)
            return error("❌ This looks like a full new training; for retraining omit dataset/algorithm re-definitions.");

        // ----- Upload νέου αρχείου (αν υπάρχει)
        String datasetId;
        if (hasFile) {
            GenericResponse<Dataset> uploadResp = datasetService.uploadDataset(file, user, DatasetFunctionalTypeEnum.TRAIN);
            datasetId = uploadResp.getDataHeader().getId().toString();
            hasDatasetId = true;
            log.info("📂 File uploaded, datasetId = {}", datasetId);
        } else {
            datasetId = request.getDatasetId();
        }

        // ----- Βρες base training αν είμαστε σε retrain mode
        Training retrainedFrom = null;
        if (hasTrainingId) {
            retrainedFrom = trainingRepository.findById(Integer.parseInt(request.getTrainingId()))
                    .orElseThrow(() -> new EntityNotFoundException("Training not found."));
            log.info("🔁 Re-training from trainingId={}", retrainedFrom.getId());
        } else if (hasModelId) {
            Model model = modelRepository.findById(Integer.parseInt(request.getModelId()))
                    .orElseThrow(() -> new EntityNotFoundException("Model not found."));
            retrainedFrom = trainingRepository.findByModel(model)
                    .orElseThrow(() -> new EntityNotFoundException("Training not found for model."));
            log.info("🔁 Re-training from modelId={}, trainingId={}", model.getId(), retrainedFrom.getId());
        }

        // ----- Resolve DatasetConfiguration + Instances (με σωστό fallback)
        Instances datasetInstances;
        DatasetConfiguration datasetConf;

        if (hasDatasetId) {
            // fresh ή retrain με νέο datasetId -> φτιάξε/ενημέρωσε DatasetConfiguration
            Dataset dataset = datasetRepository.findById(Integer.parseInt(datasetId))
                    .orElseThrow(() -> new EntityNotFoundException("Dataset not found: id=" + datasetId));

            datasetConf = new DatasetConfiguration();
            datasetConf.setDataset(dataset);
            if (hasBasicCols) datasetConf.setBasicAttributesColumns(request.getBasicCharacteristicsColumns());
            if (hasTargetCol) datasetConf.setTargetColumn(request.getTargetClassColumn());
            datasetConf.setUploadDate(ZonedDateTime.now()); // ✅ ευθυγράμμιση με το clustering branch
            datasetConf = datasetConfigurationRepository.save(datasetConf);

            // Φόρτωση από MinIO μέσω DatasetService (✅ όχι απευθείας MinioService)
            datasetInstances = datasetService.loadTrainingInstances(datasetConf);

        } else if (hasDatasetConfId) {
            datasetConf = datasetConfigurationRepository.findById(Integer.parseInt(request.getDatasetConfigurationId()))
                    .orElseThrow(() -> new EntityNotFoundException("DatasetConfiguration not found."));

            // Επίτρεψε overrides ΜΟΝΟ αν δόθηκαν (αλλιώς κράτα το ίδιο)
            if (hasBasicCols) datasetConf.setBasicAttributesColumns(request.getBasicCharacteristicsColumns());
            if (hasTargetCol) datasetConf.setTargetColumn(request.getTargetClassColumn());
            datasetConf = datasetConfigurationRepository.save(datasetConf);

            datasetInstances = datasetService.loadTrainingInstances(datasetConf); // ✅

        } else if (retrainMode) {
            // ✅ Fallback: πάρε το ΠΡΟΗΓΟΥΜΕΝΟ DatasetConfiguration
            DatasetConfiguration baseConf = retrainedFrom.getDatasetConfiguration();
            if (baseConf == null)
                return error("❌ Base training has no DatasetConfiguration.");

            // Αν δεν δίνονται overrides, ΧΡΗΣΙΜΟΠΟΙΗΣΕ ΤΟ ΙΔΙΟ conf (χωρίς νέο save, αποφεύγεις duplicate)
            if (!hasBasicCols && !hasTargetCol) {
                datasetConf = baseConf;
            } else {
                // Αν υπάρχουν overrides, κλωνοποίησε για να μην αλλοιώσεις το παλιό
                datasetConf = new DatasetConfiguration();
                datasetConf.setDataset(baseConf.getDataset());
                datasetConf.setBasicAttributesColumns(
                        hasBasicCols ? request.getBasicCharacteristicsColumns() : baseConf.getBasicAttributesColumns());
                datasetConf.setTargetColumn(
                        hasTargetCol ? request.getTargetClassColumn() : baseConf.getTargetColumn());
                datasetConf.setUploadDate(ZonedDateTime.now());
                datasetConf = datasetConfigurationRepository.save(datasetConf);
            }

            datasetInstances = datasetService.loadTrainingInstances(datasetConf); // ✅

        } else {
            return error("❌ No valid dataset provided.");
        }

        // ----- Resolve AlgorithmConfiguration (με υποστήριξη algorithmConfigurationId & retrain fallback)
        AlgorithmConfiguration algorithmConf;

        if (hasAlgorithmConfId) {
            algorithmConf = algorithmConfigurationRepository.findById(Integer.parseInt(request.getAlgorithmConfigurationId()))
                    .orElseThrow(() -> new EntityNotFoundException("AlgorithmConfiguration not found."));
            // Αν δόθηκαν options, μπορείς να επιλέξεις να τα αγνοήσεις ή να επιτρέψεις override.
            if (hasAlgorithmOpts) {
                algorithmConf = new AlgorithmConfiguration(algorithmConf.getAlgorithm());
                algorithmConf.setUser(user);
                algorithmConf.setOptions(request.getOptions());
                algorithmConf = algorithmConfigurationRepository.save(algorithmConf);
            }
        } else if (hasAlgorithmId) {
            Algorithm algorithm = algorithmRepository.findById(Integer.parseInt(request.getAlgorithmId()))
                    .orElseThrow(() -> new EntityNotFoundException("Algorithm not found."));

            algorithmConf = new AlgorithmConfiguration(algorithm);
            algorithmConf.setUser(user);
            algorithmConf.setOptions(hasAlgorithmOpts ? request.getOptions() : algorithm.getDefaultOptions());
            algorithmConf = algorithmConfigurationRepository.save(algorithmConf);

        } else if (retrainMode) {
            // ✅ Χρησιμοποίησε ΤΟ ΙΔΙΟ algorithm configuration από το base training (με προαιρετικά overrides)
            AlgorithmConfiguration baseAlgConf = retrainedFrom.getAlgorithmConfiguration();
            if (baseAlgConf == null)
                return error("❌ Base training has no AlgorithmConfiguration.");

            if (!hasAlgorithmOpts) {
                // Χρησιμοποίησε το ίδιο config (καθόλου νέο save -> αποφεύγεις duplicates)
                algorithmConf = baseAlgConf;
            } else {
                // Αν έστειλε νέα options, κλωνοποίησε/δημιούργησε νέο για να κρατήσεις το παλιό αναλλοίωτο
                algorithmConf = new AlgorithmConfiguration(baseAlgConf.getAlgorithm());
                algorithmConf.setUser(user);
                algorithmConf.setOptions(request.getOptions());
                algorithmConf = algorithmConfigurationRepository.save(algorithmConf);
            }
        } else {
            return error("❌ Algorithm information is required (algorithmId or algorithmConfigurationId).");
        }

        // ----- Προαιρετικός συμβατότητας έλεγχος (τύπος αλγορίθμου vs. dataset schema)
        AlgorithmTypeEnum type = algorithmConf.getAlgorithm().getType().getName();
        if (type == AlgorithmTypeEnum.CLASSIFICATION) {
            // π.χ. έλεγξε ότι targetColumn είναι nominal (ή θα γίνει force nominal downstream όπως έχεις)
        } else if (type == AlgorithmTypeEnum.REGRESSION) {
            // π.χ. numeric target
        } else if (type == AlgorithmTypeEnum.CLUSTERING) {
            // π.χ. target δεν απαιτείται
        }

        // ----- Training
        TrainingStatus trainStatus = trainingStatusRepository.findByName(TrainingStatusEnum.REQUESTED)
                .orElseThrow(() -> new EntityNotFoundException("Training status not found."));

        Training training = new Training();
        training.setUser(user);
        training.setStatus(trainStatus);
        training.setAlgorithmConfiguration(algorithmConf);
        training.setDatasetConfiguration(datasetConf);
        training.setRetrainedFrom(retrainedFrom); // ✅ κρατάμε lineage
        training = trainingRepository.save(training);

        log.info("✅ Training input ready: trainingId={}, datasetConfId={}, algorithmConfId={}",
                training.getId(), datasetConf.getId(), algorithmConf.getId());

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
