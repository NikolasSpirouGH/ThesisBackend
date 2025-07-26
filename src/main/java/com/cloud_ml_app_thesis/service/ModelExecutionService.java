package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.model.ModelExecution;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.ModelExecutionStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.model.ModelExecutionRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.ModelExecutionStatusRepository;
import com.cloud_ml_app_thesis.util.DatasetUtil;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelExecutionService {

    private final DatasetService datasetService;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final ModelRepository modelRepository;
    private final UserRepository userRepository;
    private final ModelService modelService;
    private final ModelExecutionRepository modelExecutionRepository;
    private final ModelExecutionStatusRepository modelExecutionStatusRepository;
    private final TaskStatusService taskStatusService;
    private final BucketResolver bucketResolver;
    private final MinioService minioService;
    private final TaskStatusRepository taskStatusRepository;

    private static final Logger logger = LoggerFactory.getLogger(ModelExecutionService.class);
    private final ModelTypeRepository modelTypeRepository;
    private final AlgorithmTypeRepository algorithmTypeRepository;

    @Transactional
    public void executePredefined(String taskId, Integer modelId, String datasetKey, User user) {
        log.info("🧠 Starting Weka prediction [taskId={}]", taskId);

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Missing task record"));

        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found with ID: " + modelId));

        if (model.getAccessibility().equals(ModelAccessibilityEnum.PRIVATE) &&
                !user.getUsername().equals(model.getTraining().getUser().getUsername())) {
            throw new AuthorizationDeniedException("You are not allowed to run this model");
        }

        if (!model.getModelType().getName().equals(ModelTypeEnum.PREDEFINED)) {
            throw new IllegalArgumentException("This model is not Weka-based");
        }

        task.setStatus(TaskStatusEnum.RUNNING);
        taskStatusRepository.save(task);

        ModelExecution execution = null;

        try {
            DatasetConfiguration config = (DatasetConfiguration) Hibernate.unproxy(model.getTraining().getDatasetConfiguration());
//            DatasetConfiguration config = datasetConfigurationRepository.findById(model.getTraining().getDatasetConfiguration().getId()).orElseThrow(() -> new EntityNotFoundException("Could not find the DatasetConfiguration for the specified Model."));
            AlgorithmTypeEnum type = model.getTraining().getAlgorithmConfiguration().getAlgorithm().getType().getName();
            AlgorithmType algorithmType = algorithmTypeRepository.findByName(type).orElseThrow(() -> new IllegalStateException("Algorithm type not found"));
            String predictBucket = bucketResolver.resolve(BucketTypeEnum.PREDICT_DATASET);
            Path csvPath = minioService.downloadObjectToTempFile(predictBucket, datasetKey);
            // ⬇️ 1. Κατέβασε και διάβασε Instances από MinIO (με μετατροπή, class fix, column filtering)

            Instances predictInstances = datasetService.loadPredictionInstancesFromCsv(
                    csvPath,
                    config,
                    algorithmType
            );

            log.info("✅ Parsed prediction dataset with {} instances and {} attributes", predictInstances.numInstances(), predictInstances.numAttributes());

            // ⬇️ 2. Φόρτωσε το εκπαιδευμένο μοντέλο
            Object modelObject = modelService.loadModel(model.getModelUrl());
            log.info("✅ Loaded model: {}", modelObject.getClass().getSimpleName());

            // ⬇️ 3. Κάνε prediction ανάλογα το μοντέλο
            List<String> predictions;
            if (modelObject instanceof Classifier classifier) {
                predictions = predictWithClassifier(classifier, predictInstances);
            } else if (modelObject instanceof Clusterer clusterer) {
                predictions = predictWithClusterer(clusterer, predictInstances);
            } else {
                throw new IllegalStateException("Unsupported model type: " + modelObject.getClass().getName());
            }

            // ⬇️ 4. Αποθήκευση αποτελεσμάτων
            execution = new ModelExecution();
            execution.setModel(model);
            execution.setExecutedByUser(user);
            execution.setExecutedAt(ZonedDateTime.now());
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.IN_PROGRESS).orElseThrow());
            modelExecutionRepository.save(execution);

            // ➕ Προσθήκη predictions στη μορφή CSV
            byte[] resultFile = DatasetUtil.replaceQuestionMarksWithPredictionResultsAsCSV(predictInstances, predictions);

            // ☁️ Upload result to MinIO
            String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
            String resultKey = user.getUsername() + "_" + timestamp + "_prediction.csv";
            String resultBucket = bucketResolver.resolve(BucketTypeEnum.PREDICTION_RESULTS);
            try (InputStream in = new ByteArrayInputStream(resultFile)) {
                minioService.uploadToMinio(in, resultBucket, resultKey, resultFile.length, "text/csv");
            }

            String minioUrl = modelService.generateMinioUrl(resultBucket, resultKey);
            execution.setPredictionResult(minioUrl);
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FINISHED).orElseThrow());
            modelExecutionRepository.save(execution);

            // ✔️ Task ολοκληρώθηκε
            taskStatusService.completeTask(taskId);
            task.setExecutionId(execution.getId());
            taskStatusRepository.save(task);

            log.info("✅ Weka prediction completed [taskId={}], result uploaded: {}", taskId, minioUrl);

        } catch (Exception e) {
            log.error("❌ Weka prediction failed [taskId={}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, e.getMessage());

            if (execution != null) {
                execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FAILED).orElse(null));
                modelExecutionRepository.save(execution);
            }

            taskStatusRepository.save(task);
            throw new RuntimeException("Prediction failed: " + e.getMessage(), e);
        }
    }


    public ByteArrayResource getExecutionResultsFile(Integer modelExecutionId, User user) {
        logger.debug("📥 Fetching result for execution id = {} by user={}", modelExecutionId, user.getUsername());

        ModelExecution modelExecution = modelExecutionRepository.findById(modelExecutionId)
                .orElseThrow(() -> new EntityNotFoundException("Model execution " + modelExecutionId + " not found"));

        // 🔐 Authorization check
        if (modelExecution.getModel().getAccessibility().equals(ModelAccessibilityEnum.PRIVATE) &&
                !modelExecution.getExecutedByUser().getUsername().equals(user.getUsername())) {
            throw new AuthorizationDeniedException("You are not authorized to access this execution result");
        }

        // 📦 Extract MinIO key and bucket
        String predictionUrl = modelExecution.getPredictionResult();
        if (predictionUrl == null || predictionUrl.isBlank()) {
            throw new IllegalStateException("This model execution has no stored result");
        }

        String resultKey = minioService.extractMinioKey(predictionUrl);
        String bucket = bucketResolver.resolve(BucketTypeEnum.PREDICTION_RESULTS);

        // 📤 Download as byte array
        byte[] resultBytes = minioService.downloadObjectAsBytes(bucket, resultKey);
        if (resultBytes.length == 0) {
            throw new FileProcessingException("The prediction result is empty", null);
        }

        logger.info("✅ Prediction result fetched from MinIO [key={}, size={} bytes]", resultKey, resultBytes.length);

        return new ByteArrayResource(resultBytes);
    }

    private List<String> predictWithClassifier(Classifier classifier, Instances dataset) throws Exception {
        logger.info("⚙️ [Classifier] Dataset info before prediction:");
        logger.info("   ➤ Number of instances: {}", dataset.numInstances());
        logger.info("   ➤ Number of attributes: {}", dataset.numAttributes());
        logger.info("   ➤ Class index: {}", dataset.classIndex());
        dataset.setClassIndex(dataset.numAttributes() - 1);
        Attribute classAttribute = dataset.classAttribute();

        if (classAttribute != null) {
            logger.info("   ➤ Class attribute name: {}", classAttribute.name());
            logger.info("   ➤ Class attribute isNominal: {}", classAttribute.isNominal());
            logger.info("   ➤ Class attribute isNumeric: {}", classAttribute.isNumeric());

            if (classAttribute.isNominal()) {
                for (int i = 0; i < classAttribute.numValues(); i++) {
                    logger.info("   ➤ Class nominal value [{}]: {}", i, classAttribute.value(i));
                }
            }
        } else {
            logger.warn("   ⚠️ Class attribute is NULL!");
        }

        logger.info("Performing prediction with classifier on dataset with {} instances", dataset.numInstances());

        List<String> predictions = new ArrayList<>();
        for (int i = 0; i < dataset.numInstances(); i++) {
            try {
                double prediction = classifier.classifyInstance(dataset.instance(i));

                String predictedValue;
                if (classAttribute.isNominal()) {
                    predictedValue = classAttribute.value((int) prediction); // Classification
                } else {
                    predictedValue = String.valueOf(prediction); // Regression
                }

                logger.info("Instance {}: Predicted value: {}", i, predictedValue);
                predictions.add(predictedValue);

            } catch (Exception e) {
                logger.error("Error predicting instance {}: {}", i, e.getMessage(), e);
                throw e;
            }
        }

        logger.info("Predictions completed successfully. Total predictions: {}", predictions.size());
        return predictions;
    }

    private List<String> predictWithClusterer(Clusterer clusterer, Instances dataset) throws Exception {
        logger.info("Performing prediction with clusterer on dataset with {} instances", dataset.numInstances());
        List<String> predictions = new ArrayList<>();
        for (int i = 0; i < dataset.numInstances(); i++) {
            try {
                int cluster = clusterer.clusterInstance(dataset.instance(i));
                String clusterLabel = "Cluster " + cluster; // Here you can map to more meaningful labels if available
                logger.info("Instance {}: Predicted cluster: {}", i, clusterLabel);
                predictions.add(clusterLabel);
            } catch (Exception e) {
                logger.error("Error predicting instance {}: {}", i, e.getMessage(), e);
                throw e; // Re-throw the exception after logging it
            }
        }
        logger.info("Cluster predictions completed successfully. Total predictions: {}", predictions.size());
        return predictions;
    }



public ByteArrayResource getPredictionResults(Integer modelExecutionId, User user) {
    ModelExecution execution = modelExecutionRepository
            .findById(modelExecutionId).orElseThrow(()-> new EntityNotFoundException("Model execution " + modelExecutionId + " not found"));

    // Already saved during initial prediction
    Instances predictInstances = null;
    try {
        predictInstances = DatasetUtil.loadPredictionDataset(execution.getDataset());
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
    byte[] result = null;
    try {
        result = DatasetUtil.replaceQuestionMarksWithPredictionResultsAsCSV(predictInstances,  Arrays.asList(execution.getPredictionResult().split(",")));
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
    return new ByteArrayResource(result);
}

}
