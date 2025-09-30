package com.cloud_ml_app_thesis.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.response.execution.ModelExecutionDTO;
import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.model.ModelExecution;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.ModelExecutionStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.exception.UserInitiatedStopException;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.model.ModelExecutionRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.ModelExecutionStatusRepository;
import com.cloud_ml_app_thesis.util.DatasetUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelExecutionService {

    private final DatasetService datasetService;
    private final ModelRepository modelRepository;
    private final UserRepository userRepository;
    private final ModelService modelService;
    private final ModelExecutionRepository modelExecutionRepository;
    private final ModelExecutionStatusRepository modelExecutionStatusRepository;
    private final TaskStatusService taskStatusService;
    private final BucketResolver bucketResolver;
    private final MinioService minioService;
    private final TaskStatusRepository taskStatusRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    private static final Logger logger = LoggerFactory.getLogger(ModelExecutionService.class);

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executePredefined(String taskId, Integer modelId, String datasetKey, User user) {
        boolean complete = false;
        ModelExecution execution = null;
        log.info("🧠 Starting Weka prediction [taskId={}]", taskId);

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Missing task record"));

        Model model = modelRepository.findByIdWithTrainingDetails(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found with ID: " + modelId));

        if (model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE) &&
                !user.getUsername().equals(model.getTraining().getUser().getUsername())) {
            throw new AuthorizationDeniedException("You are not allowed to run this model");
        }

        if (!model.getModelType().getName().equals(ModelTypeEnum.PREDEFINED)) {
            throw new IllegalArgumentException("This model is not Weka-based");
        }

        task.setStatus(TaskStatusEnum.RUNNING);
        taskStatusRepository.save(task);
        entityManager.detach(task);

        try {
            // Check for stop request before starting
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop for task " + taskId);
            }
            DatasetConfiguration config = model.getTraining().getDatasetConfiguration();
            if (config == null) {
                throw new IllegalStateException("Model " + modelId + " does not have an associated dataset configuration");
            }

            AlgorithmConfiguration algorithmConfiguration = model.getTraining().getAlgorithmConfiguration();
            if (algorithmConfiguration == null) {
                throw new IllegalStateException("Model " + modelId + " does not have an associated algorithm configuration");
            }

            AlgorithmType algorithmType = algorithmConfiguration.getAlgorithmType();
            if (algorithmType == null) {
                throw new IllegalStateException("Algorithm configuration " + algorithmConfiguration.getId() + " is missing algorithm type");
            }
            String predictBucket = bucketResolver.resolve(BucketTypeEnum.PREDICT_DATASET);
            Path csvPath = minioService.downloadObjectToTempFile(predictBucket, datasetKey);

            logger.info("Model type : {}", model.getTraining().getAlgorithmConfiguration().getAlgorithm().getType());
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
            boolean isClsuterer = false;
            if (modelObject instanceof Classifier classifier) {
                predictions = predictWithClassifier(classifier, predictInstances);
            } else if (modelObject instanceof Clusterer clusterer) {
                predictions = predictWithClusterer(clusterer, predictInstances);
                isClsuterer = true;
            } else {
                throw new IllegalStateException("Unsupported model type: " + modelObject.getClass().getName());
            }

            // ⬇️ 4. Create ModelExecution record
            execution = new ModelExecution();
            execution.setModel(model);
            execution.setExecutedByUser(user);
            execution.setDataset(config.getDataset());
            execution.setExecutedAt(ZonedDateTime.now());
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.IN_PROGRESS).orElseThrow());
            modelExecutionRepository.save(execution);
            entityManager.detach(execution);

            logger.info("Attributes Columns: {}", predictInstances.numAttributes());
            logger.info("Target Class Column: {}", config.getTargetColumn());

            // Check for stop request after model loading
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop after model loading for task " + taskId);
            }

            // ➕ Generate predictions and CSV
            byte[] resultFile = DatasetUtil.replaceQuestionMarksWithPredictionResultsAsCSV(predictInstances, predictions, isClsuterer);

            // ☁️ Upload result to MinIO
            String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
            String resultKey = user.getUsername() + "_" + timestamp + "_prediction.csv";
            String resultBucket = bucketResolver.resolve(BucketTypeEnum.PREDICTION_RESULTS);
            try (InputStream in = new ByteArrayInputStream(resultFile)) {
                minioService.uploadToMinio(in, resultBucket, resultKey, resultFile.length, "text/csv");
            }

            String minioUrl = modelService.generateMinioUrl(resultBucket, resultKey);
            complete = true;

            // Check for stop request after upload
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop after result upload for task " + taskId);
            }

            // ⬇️ Update ModelExecution in separate transaction
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            final Integer executionId = execution.getId();
            final String finalMinioUrl = minioUrl;
            transactionTemplate.executeWithoutResult(status -> {
                ModelExecution exec = modelExecutionRepository.findById(executionId)
                        .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                exec.setPredictionResult(finalMinioUrl);
                exec.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FINISHED).orElseThrow());
                modelExecutionRepository.saveAndFlush(exec);
            });

            // ⬇️ Complete async task in separate transaction
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            final Integer finalExecutionId = execution.getId();
            transactionTemplate.executeWithoutResult(status -> {
                if (taskStatusService.stopRequested(taskId)) {
                    return; // Don't complete if stopped
                }
                AsyncTaskStatus freshTask = taskStatusRepository.findById(taskId).orElseThrow();
                freshTask.setStatus(TaskStatusEnum.COMPLETED);
                freshTask.setFinishedAt(ZonedDateTime.now());
                freshTask.setExecutionId(finalExecutionId);
                taskStatusRepository.saveAndFlush(freshTask);
            });

            log.info("✅ Weka prediction completed [taskId={}], result uploaded: {}", taskId, minioUrl);

        } catch (UserInitiatedStopException e) {
            log.warn("🛑 Execution manually stopped by user [taskId={}]: {}", taskId, e.getMessage());

            taskStatusService.taskStoppedExecution(taskId, execution != null ? execution.getId() : null);

            ModelExecutionStatusEnum finalStatus = complete
                    ? ModelExecutionStatusEnum.FINISHED
                    : ModelExecutionStatusEnum.FAILED;

            // Update execution status in separate transaction
            if (execution != null) {
                final Integer executionId = execution.getId();
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.executeWithoutResult(status -> {
                    ModelExecution exec = modelExecutionRepository.findById(executionId)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                    exec.setStatus(modelExecutionStatusRepository.findByName(finalStatus)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecutionStatus not found")));
                    modelExecutionRepository.saveAndFlush(exec);
                });
            }
        } catch (Exception e) {
            log.error("❌ Weka prediction failed [taskId={}]: {}", taskId, e.getMessage(), e);

            // Task -> FAILED
            taskStatusService.taskFailed(taskId, e.getMessage());

            // ModelExecution -> FAILED in separate transaction
            if (execution != null) {
                final Integer executionId = execution.getId();
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.executeWithoutResult(status -> {
                    ModelExecution exec = modelExecutionRepository.findById(executionId)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                    exec.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FAILED)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecutionStatus FAILED not found")));
                    modelExecutionRepository.saveAndFlush(exec);
                });
            }

            throw new RuntimeException("Prediction failed: " + e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executeCustom(String taskId, Integer modelId, String datasetKey, User user) {
        boolean complete = false;
        ModelExecution execution = null;
        log.info("🧠 Starting Custom model prediction [taskId={}]", taskId);

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Missing task record"));

        Model model = modelRepository.findByIdWithTrainingDetails(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found with ID: " + modelId));

        if (model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE) &&
                !user.getUsername().equals(model.getTraining().getUser().getUsername())) {
            throw new AuthorizationDeniedException("You are not allowed to run this model");
        }

        if (!model.getModelType().getName().equals(ModelTypeEnum.CUSTOM)) {
            throw new IllegalArgumentException("This model is not Custom-based");
        }

        task.setStatus(TaskStatusEnum.RUNNING);
        taskStatusRepository.save(task);
        entityManager.detach(task);

        try {
            // Check for stop request before starting
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop for task " + taskId);
            }

            DatasetConfiguration datasetConfiguration = model.getTraining().getDatasetConfiguration();
            if (datasetConfiguration == null) {
                throw new IllegalStateException("Model " + modelId + " does not have an associated dataset configuration");
            }

            // ⬇️ Create ModelExecution record
            execution = new ModelExecution();
            execution.setModel(model);
            execution.setExecutedByUser(user);
            execution.setDataset(datasetConfiguration.getDataset());
            execution.setExecutedAt(ZonedDateTime.now());
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.IN_PROGRESS).orElseThrow());
            modelExecutionRepository.save(execution);
            entityManager.detach(execution);

            log.info("🐳 Custom model execution started - this would involve Docker container execution");
            log.info("📝 Note: Custom model execution not fully implemented yet");

            // TODO: Implement custom model execution with Docker containers
            // This would involve similar logic to CustomTrainingService but for prediction

            // For now, just simulate completion
            Thread.sleep(5000);

            // Check for stop request after simulation
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop after simulation for task " + taskId);
            }

            complete = true;
            String simulatedResult = "Custom prediction completed successfully";

            // ⬇️ Update ModelExecution in separate transaction
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            final Integer executionId = execution.getId();
            final String finalResult = simulatedResult;
            transactionTemplate.executeWithoutResult(status -> {
                ModelExecution exec = modelExecutionRepository.findById(executionId)
                        .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                exec.setPredictionResult(finalResult);
                exec.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FINISHED).orElseThrow());
                modelExecutionRepository.saveAndFlush(exec);
            });

            // ⬇️ Complete async task in separate transaction
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            final Integer finalExecutionId = execution.getId();
            transactionTemplate.executeWithoutResult(status -> {
                if (taskStatusService.stopRequested(taskId)) {
                    return; // Don't complete if stopped
                }
                AsyncTaskStatus freshTask = taskStatusRepository.findById(taskId).orElseThrow();
                freshTask.setStatus(TaskStatusEnum.COMPLETED);
                freshTask.setFinishedAt(ZonedDateTime.now());
                freshTask.setExecutionId(finalExecutionId);
                taskStatusRepository.saveAndFlush(freshTask);
            });

            log.info("✅ Custom model prediction completed [taskId={}]", taskId);

        } catch (UserInitiatedStopException e) {
            log.warn("🛑 Custom execution manually stopped by user [taskId={}]: {}", taskId, e.getMessage());

            taskStatusService.taskStoppedExecution(taskId, execution != null ? execution.getId() : null);

            ModelExecutionStatusEnum finalStatus = complete
                    ? ModelExecutionStatusEnum.FINISHED
                    : ModelExecutionStatusEnum.FAILED;

            // Update execution status in separate transaction
            if (execution != null) {
                final Integer executionId = execution.getId();
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.executeWithoutResult(status -> {
                    ModelExecution exec = modelExecutionRepository.findById(executionId)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                    exec.setStatus(modelExecutionStatusRepository.findByName(finalStatus)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecutionStatus not found")));
                    modelExecutionRepository.saveAndFlush(exec);
                });
            }
        } catch (Exception e) {
            log.error("❌ Custom model prediction failed [taskId={}]: {}", taskId, e.getMessage(), e);

            // Task -> FAILED
            taskStatusService.taskFailed(taskId, e.getMessage());

            // ModelExecution -> FAILED in separate transaction
            if (execution != null) {
                final Integer executionId = execution.getId();
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.executeWithoutResult(status -> {
                    ModelExecution exec = modelExecutionRepository.findById(executionId)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecution not found"));
                    exec.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.FAILED)
                            .orElseThrow(() -> new EntityNotFoundException("ModelExecutionStatus FAILED not found")));
                    modelExecutionRepository.saveAndFlush(exec);
                });
            }

            throw new RuntimeException("Custom model prediction failed: " + e.getMessage(), e);
        }
    }

    public ByteArrayResource getExecutionResultsFile(Integer modelExecutionId, User user) {
        logger.debug("📥 Fetching result for execution id = {} by user={}", modelExecutionId, user.getUsername());

        ModelExecution modelExecution = modelExecutionRepository.findById(modelExecutionId)
                .orElseThrow(() -> new EntityNotFoundException("Model execution " + modelExecutionId + " not found"));

        // 🔐 Authorization check
        if (modelExecution.getModel().getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE) &&
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

    public List<String> predictWithClassifier(Classifier classifier, Instances instances) throws Exception {
        List<String> predictions = new ArrayList<>();

        if (instances.classIndex() == -1) {
            throw new IllegalStateException("❌ Class index is not set in the prediction dataset.");
        }

        Attribute classAttr = instances.classAttribute();
        log.info("⚙️ [Classifier] Dataset info before prediction:");
        log.info("    ➤ Number of instances: {}", instances.numInstances());
        log.info("    ➤ Number of attributes: {}", instances.numAttributes());
        log.info("    ➤ Class index: {}", instances.classIndex());
        log.info("    ➤ Class attribute name: {}", classAttr.name());
        log.info("    ➤ Class attribute isNominal: {}", classAttr.isNominal());
        log.info("    ➤ Class attribute isNumeric: {}", classAttr.isNumeric());

        for (int i = 0; i < instances.numInstances(); i++) {
            Instance instance = instances.instance(i);
            double pred = classifier.classifyInstance(instance);

            String predictedLabel;

            if (classAttr.isNominal()) {
                // Classification: return label
                int classIndex = (int) pred;
                predictedLabel = classAttr.value(classIndex);
            } else {
                // Regression: return value
                predictedLabel = String.valueOf(pred);
            }

            predictions.add(predictedLabel);
            log.info("📌 Instance {}: Predicted value: {}", i, predictedLabel);
        }

        log.info("🔍 Classifier class labels: {}", Arrays.toString(instances.classAttribute().toString().split(",")));
        log.info("✅ Predictions completed successfully. Total: {}", predictions.size());
        return predictions;
    }

//    private List<String> predictWithClusterer(Clusterer clusterer, Instances dataset) throws Exception {
//        logger.info("Performing prediction with clusterer on dataset with {} instances", dataset.numInstances());
//        List<String> predictions = new ArrayList<>();
//        for (int i = 0; i < dataset.numInstances(); i++) {
//            try {
//                int cluster = clusterer.clusterInstance(dataset.instance(i));
//                String clusterLabel = "Cluster " + cluster; // Here you can map to more meaningful labels if available
//                logger.info("Instance {}: Predicted cluster: {}", i, clusterLabel);
//                predictions.add(clusterLabel);
//            } catch (Exception e) {
//                logger.error("Error predicting instance {}: {}", i, e.getMessage(), e);
//                throw e; // Re-throw the exception after logging it
//            }
//        }
//        logger.info("Cluster predictions completed successfully. Total predictions: {}", predictions.size());
//        return predictions;
//    }

    private List<String> predictWithClusterer(Clusterer clusterer, Instances dataset) throws Exception {
        logger.info("Performing prediction with clusterer on dataset with {} instances", dataset.numInstances());
        List<String> predictions = new ArrayList<>();
        for (int i = 0; i < dataset.numInstances(); i++) {
            int cluster = clusterer.clusterInstance(dataset.instance(i));
            logger.info("Instance {}: Predicted cluster: {}", i, cluster);
            predictions.add(String.valueOf(cluster));  // 👈 απλό αριθμητικό string
        }
        return predictions;
    }

    public List<ModelExecutionDTO> getUserExecutions(User user) {
        log.info("📋 Retrieving executions for user: {}", user.getUsername());

        List<ModelExecution> executions = modelExecutionRepository.findByExecutedByUserWithDetails(user);

        return executions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private ModelExecutionDTO convertToDTO(ModelExecution execution) {
        Model model = execution.getModel();

        return ModelExecutionDTO.builder()
                .id(execution.getId())
                .modelName(model.getName())
                .modelType(model.getModelType().getName().toString())
                .algorithmName(getAlgorithmName(model))
                .datasetName(execution.getDataset() != null ? execution.getDataset().getFileName(): "Unknown Dataset")
                .executedAt(execution.getExecutedAt())
                .status(execution.getStatus().getName().toString())
                .predictionResult(execution.getPredictionResult())
                .modelId(model.getId())
                .datasetId(execution.getDataset() != null ? execution.getDataset().getId() : null)
                .hasResultFile(execution.getPredictionResult() != null && !execution.getPredictionResult().isEmpty())
                .build();
    }

    @Transactional
    public void deleteExecution(Integer executionId, User user) {
        log.info("🗑️ Attempting to delete execution [executionId={}, user={}]", executionId, user.getUsername());

        ModelExecution execution = modelExecutionRepository.findById(executionId)
                .orElseThrow(() -> new EntityNotFoundException("Model execution not found with ID: " + executionId));

        // Authorization check: only the user who executed can delete
        if (!execution.getExecutedByUser().getUsername().equals(user.getUsername())) {
            throw new AuthorizationDeniedException("You are not authorized to delete this execution");
        }

        // Delete the prediction result file from MinIO if exists
        if (execution.getPredictionResult() != null && !execution.getPredictionResult().isEmpty()) {
            try {
                String resultKey = minioService.extractMinioKey(execution.getPredictionResult());
                String bucket = bucketResolver.resolve(BucketTypeEnum.PREDICTION_RESULTS);
                minioService.deleteObject(bucket, resultKey);
                log.info("🗑️ Deleted prediction result file from MinIO [key={}]", resultKey);
            } catch (Exception e) {
                log.warn("⚠️ Failed to delete prediction result file from MinIO: {}", e.getMessage());
                // Continue with database deletion even if MinIO deletion fails
            }
        }

        // Delete execution record from database
        modelExecutionRepository.delete(execution);
        log.info("✅ Execution deleted successfully [executionId={}]", executionId);
    }

    private String getAlgorithmName(Model model) {
        try {
            if (model.getModelType().getName().toString().equals("WEKA")) {
                return model.getTraining() != null &&
                       model.getTraining().getAlgorithmConfiguration() != null &&
                       model.getTraining().getAlgorithmConfiguration().getAlgorithm() != null
                       ? model.getTraining().getAlgorithmConfiguration().getAlgorithm().getName()
                       : "Unknown Algorithm";
            } else if (model.getModelType().getName().toString().equals("CUSTOM")) {
                return model.getTraining() != null &&
                       model.getTraining().getCustomAlgorithmConfiguration() != null &&
                       model.getTraining().getCustomAlgorithmConfiguration().getAlgorithm() != null
                       ? model.getTraining().getCustomAlgorithmConfiguration().getAlgorithm().getName()
                       : "Unknown Custom Algorithm";
            }
            return "Unknown Algorithm";
        } catch (Exception e) {
            log.warn("Error getting algorithm name for model {}: {}", model.getId(), e.getMessage());
            return "Unknown Algorithm";
        }
    }
}
