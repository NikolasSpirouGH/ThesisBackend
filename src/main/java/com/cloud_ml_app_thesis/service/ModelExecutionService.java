package com.cloud_ml_app_thesis.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.cloud_ml_app_thesis.dto.request.execution.ModelExecutionSearchRequest;
import com.cloud_ml_app_thesis.dto.request.model.ModelSearchRequest;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelExecutionAccessibilityEnum;
import com.github.dockerjava.api.exception.UnauthorizedException;
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
import com.cloud_ml_app_thesis.dto.request.execution.ModelExecutionDTO;
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
import com.cloud_ml_app_thesis.util.DateUtil;
import com.nimbusds.oauth2.sdk.util.StringUtils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;

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
    private final DateUtil dateUtil;

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

            // 🔄 Convert numeric class to nominal for classification algorithms (if needed)
            if (algorithmType.getName() == com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum.CLASSIFICATION
                && predictInstances.classIndex() >= 0
                && predictInstances.classAttribute().isNumeric()) {

                log.info("🔄 Converting numeric class to nominal for classification prediction");
                log.info("   Class attribute: {} (numeric)", predictInstances.classAttribute().name());

                NumericToNominal convert = new NumericToNominal();
                String classIndexStr = String.valueOf(predictInstances.classIndex() + 1);
                convert.setAttributeIndices(classIndexStr);
                convert.setInputFormat(predictInstances);
                predictInstances = Filter.useFilter(predictInstances, convert);

                log.info("✅ Converted class to nominal for prediction. Values: {}", predictInstances.classAttribute().toString());
            }

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
            execution.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.RUNNING).orElseThrow());
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
                exec.setStatus(modelExecutionStatusRepository.findByName(ModelExecutionStatusEnum.COMPLETED).orElseThrow());
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
                    ? ModelExecutionStatusEnum.COMPLETED
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

    @Transactional(readOnly = true)
    public List<ModelExecutionDTO> getUserExecutions(
            User user,
            LocalDate fromDate,
            LocalDate toDate,
            Integer modelId,
            ModelExecutionStatusEnum status) {

        log.info("📋 Fetching executions for user {} with filters [fromDate={}, toDate={}, modelId={}, status={}]",
                user.getId(), fromDate, toDate, modelId, status);

        // Get all executions for user (simple query - same pattern as ModelService)
        List<ModelExecution> executions = modelExecutionRepository.findByExecutedByUserWithDetails(user);

        // Convert LocalDate to ZonedDateTime for filtering
        ZonedDateTime fromDateTime = fromDate != null
                ? fromDate.atStartOfDay(ZoneId.systemDefault())
                : null;
        ZonedDateTime toDateTime = toDate != null
                ? toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault())
                : null;

        // Filter in-memory (same pattern as ModelService)
        List<ModelExecution> filteredExecutions = executions.stream()
                .filter(execution -> {
                    // Filter by date range
                    if (fromDateTime != null && execution.getExecutedAt().isBefore(fromDateTime)) {
                        return false;
                    }
                    if (toDateTime != null && execution.getExecutedAt().isAfter(toDateTime)) {
                        return false;
                    }
                    // Filter by model ID
                    if (modelId != null && !execution.getModel().getId().equals(modelId)) {
                        return false;
                    }
                    // Filter by status
                    if (status != null && !execution.getStatus().getName().equals(status)) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        log.info("✅ Found {} executions for user {} (filtered from {})",
                filteredExecutions.size(), user.getId(), executions.size());

        return filteredExecutions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ModelExecutionDTO> searchExecutions(ModelExecutionSearchRequest request, User user) {
        ModelExecutionSearchRequest criteria = request != null ? request : new ModelExecutionSearchRequest();

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName().name().equals("ADMIN"));

        log.info("User {} isAdmin: {}", user.getUsername(), isAdmin);

        List<ModelExecution> executions = isAdmin
                ? modelExecutionRepository.findAllWithDetails()
                : modelExecutionRepository.findAccessibleToUser(user.getId());

        log.info("Found {} executions before filtering", executions.size());

        List<ModelExecutionDTO> filtered = executions.stream()
                .filter(execution -> matchExecutionCriteria(execution, criteria))
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        log.info("Returning {} executions after filtering", filtered.size());

        return filtered;
    }

    private boolean matchExecutionCriteria(ModelExecution execution, ModelExecutionSearchRequest request) {
        List<Boolean> matches = new ArrayList<>();

        boolean hasExecutedAtFrom = StringUtils.isNotBlank(request.getExecutedAtFrom()) && !request.getExecutedAtFrom().equalsIgnoreCase("string");
        boolean hasExecutedAtTo = StringUtils.isNotBlank(request.getExecutedAtTo()) && !request.getExecutedAtTo().equalsIgnoreCase("string");
        if (hasExecutedAtFrom || hasExecutedAtTo) {
            if (execution.getExecutedAt() == null) {
                matches.add(false);
            } else {
                LocalDateTime executedAt = execution.getExecutedAt().toLocalDateTime();
                boolean dateMatch = true;

                if (hasExecutedAtFrom) {
                    LocalDateTime from = dateUtil.parseDateTime(request.getExecutedAtFrom(), true);
                    dateMatch = dateMatch && (executedAt.isAfter(from) || executedAt.isEqual(from));
                }
                if (hasExecutedAtTo) {
                    LocalDateTime to = dateUtil.parseDateTime(request.getExecutedAtTo(), false);
                    dateMatch = dateMatch && (executedAt.isBefore(to) || executedAt.isEqual(to));
                }
                matches.add(dateMatch);
            }
        }
        if (matches.isEmpty()) {
            return true;
        }

        // All date conditions must be satisfied
        return matches.stream().allMatch(Boolean::booleanValue);
    }

    private ModelExecutionDTO convertToDTO(ModelExecution execution) {
        Model model = execution.getModel();

        return ModelExecutionDTO.builder()
                .id(execution.getId())
                .modelName(model != null ? model.getName() : "Unknown Model")
                .modelType(model != null ? model.getModelType().getName().toString() : "UNKNOWN")
                .algorithmName(model != null ? getAlgorithmName(model) : "Unknown Algorithm")
                .datasetName(execution.getDataset() != null ? execution.getDataset().getFileName(): "Unknown Dataset")
                .executedAt(execution.getExecutedAt())
                .status(execution.getStatus() != null ? execution.getStatus().getName().toString() : "UNKNOWN")
                .predictionResult(execution.getPredictionResult())
                .modelId(model != null ? model.getId() : null)
                .datasetId(execution.getDataset() != null ? execution.getDataset().getId() : null)
                .hasResultFile(execution.getPredictionResult() != null && !execution.getPredictionResult().isEmpty())
                .ownerUsername(execution.getExecutedByUser() != null ? execution.getExecutedByUser().getUsername() : "Unknown")
                .accessibility(execution.getAccessibility() != null ? execution.getAccessibility().getName().toString() : "PRIVATE")
                .build();
    }

    @Transactional
    public void deleteExecution(Integer executionId, User user) {
        log.info("🗑️ Attempting to delete execution [executionId={}, user={}]", executionId, user.getUsername());

        ModelExecution execution = modelExecutionRepository.findById(executionId)
                .orElseThrow(() -> new EntityNotFoundException("Model execution not found with ID: " + executionId));

        if(!execution.getExecutedByUser().getUsername().equals(user.getUsername())
                && !execution.getAccessibility().getName().equals(ModelExecutionAccessibilityEnum.PUBLIC)) {
            throw new UnauthorizedException("You are not authorized to access this execution");
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

    @Transactional(readOnly = true)
    public ModelExecutionDTO getExecutionDetails(Integer executionId, User user) {
        log.info("Fetching execution details [executionId={}, user={}]", executionId, user.getUsername());

        ModelExecution execution = modelExecutionRepository.findById(executionId)
                .orElseThrow(() -> new EntityNotFoundException("Model execution not found with ID: " + executionId));

        if(!execution.getExecutedByUser().getUsername().equals(user.getUsername())
                && !execution.getAccessibility().getName().equals(ModelExecutionAccessibilityEnum.PUBLIC)) {
            throw new UnauthorizedException("You are not authorized to access this execution");
        }

        log.info("Execution details retrieved successfully [executionId={}]", executionId);
        return convertToDTO(execution);
    }

}
