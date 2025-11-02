package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.model.ModelDTO;
import com.cloud_ml_app_thesis.dto.request.model.ModelFinalizeRequest;
import com.cloud_ml_app_thesis.dto.request.model.ModelSearchRequest;
import com.cloud_ml_app_thesis.dto.train.ClusterEvaluationResult;
import com.cloud_ml_app_thesis.dto.train.EvaluationResult;
import com.cloud_ml_app_thesis.dto.train.RegressionEvaluationResult;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.accessibility.ModelAccessibility;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.ModelStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.accessibility.ModelAccessibilityRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.ModelTypeRepository;
import com.cloud_ml_app_thesis.repository.status.ModelStatusRepository;
import com.cloud_ml_app_thesis.util.AlgorithmUtil;
import com.cloud_ml_app_thesis.util.DateUtil;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.PrincipalComponents;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.clusterers.ClusterEvaluation;
import weka.clusterers.Clusterer;
import weka.core.Instance;
import weka.core.Instances;


import java.awt.geom.Point2D;
import java.io.*;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelService {

    private final MinioClient minioClient;
    private final MinioService minioService;

    private final ModelRepository modelRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ModelStatusRepository modelStatusRepository;
    private final ModelAccessibilityRepository accessibilityRepository;
    private final DateUtil dateUtil;

    private final BucketResolver bucketResolver;
    private static final Logger logger = LoggerFactory.getLogger(ModelService.class);

    private final TrainingRepository trainingRepository;
    private final ModelAccessibilityRepository modelAccessibilityRepository;
    private final ModelTypeRepository modelTypeRepository;

    @Value("${minio.url}")
    private String minioUrl;

    public String saveModelToMinio(String bucketName, String objectName, byte[] data) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(
                                bais, data.length, -1)
                        .build());
        return minioUrl + "/" + bucketName + "/" + objectName;
    }

    public byte[] serializeModel(Object model) throws Exception {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(model);
            return bos.toByteArray();
        }
    }

    @Transactional
    public void finalizeModel(Integer modelId, UserDetails userDetails, ModelFinalizeRequest request) {
        log.info("🔐 Finalizing model for modelId={} by user={}", modelId, userDetails.getUsername());

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Model model = modelRepository.findById(modelId).orElseThrow(() -> new EntityNotFoundException("Model not found"));

        if (!model.getTraining().getUser().getUsername().equals(user.getUsername())) {
            throw new AccessDeniedException("You do not own this model.");
        }

        if (!model.getTraining().getStatus().getName().toString().equalsIgnoreCase("COMPLETED")) {
            throw new IllegalStateException("Training must be completed before finalizing a model.");
        }
        Training training = model.getTraining();

        if (model == null) {
            throw new IllegalStateException("No model found for this training.");
        }

        if(model.isFinalized()) {
            throw new IllegalStateException("Model is finalized.");
        }

        log.info("🧩 Setting model metadata...");
        model.setName(request.getName());
        log.info("✔️ modelName = {}", request.getName());

        model.setDescription(request.getDescription());
        log.info("✔️ modelDescription = {}", request.getDescription());

        model.setDataDescription(request.getDataDescription());
        log.info("✔ dataDescription = {}", training.getDatasetConfiguration().getDataset().getDescription());

        model.setFinalizationDate(ZonedDateTime.now());
        log.info("✔️ finalizationDate = {}", model.getFinalizationDate());

        model.setStatus(modelStatusRepository.findByName(ModelStatusEnum.FINISHED).orElseThrow(() -> new EntityNotFoundException("Model status not found")));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));
        model.setCategory(category);
        log.info("✔️ category = {}", category.getName());

        log.info("🔍 request.isPublic() = {}", request.isPublic());
        ModelAccessibilityEnum accessEnum = request.isPublic() ? ModelAccessibilityEnum.PUBLIC : ModelAccessibilityEnum.PRIVATE;
        ModelAccessibility accessibility = accessibilityRepository.findByName(accessEnum)
                .orElseThrow(() -> new IllegalArgumentException("Invalid accessibility: " + accessEnum));
        model.setAccessibility(accessibility);
        log.info("✔️ accessibility = {}", accessEnum);

        model.setKeywords(new ArrayList<>(request.getKeywords()));
        log.info("✔️ keywords = {}", request.getKeywords());

        model.setFinalized(true);
        modelRepository.save(model);
        log.info("✅ Model with ID={} finalized successfully", model.getId());
    }

    public void saveModel(Training training, String modelUrl, String metricsUrl, ModelType modelType) {
        ModelAccessibility accessibility = modelAccessibilityRepository
                .findByName(ModelAccessibilityEnum.PRIVATE)
                .orElseThrow(() -> new EntityNotFoundException("Could not find accessibility"));

        Category defaultCategory = categoryRepository
                .findByName("Default")
                .orElseThrow(() -> new EntityNotFoundException("Could not find default category"));
        Model model = new Model();
        model.setTraining(training);
        model.setModelUrl(modelUrl);
        model.setMetricsUrl(metricsUrl);
        model.setStatus(modelStatusRepository.findByName(ModelStatusEnum.IN_PROGRESS)
                .orElseThrow(() -> new EntityNotFoundException("Could not find IN_PROGRESS model status")));
        model.setModelType(modelType);
        //ZonedDateTime finishedZoned = training.getFinishedDate().withZoneSameInstant(ZoneId.of("Europe/Athens"));
        model.setCreatedAt(ZonedDateTime.now());
        model.setAccessibility(accessibility);
        model.setCategory(defaultCategory);

        modelRepository.save(model);
    }

    public ByteArrayResource getMetricsFile(Integer id, User user) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        boolean isOwner = model.getTraining().getUser().getUsername().equals(user.getUsername());
        if (!isOwner && model.getAccessibility().getName().equals(ModelAccessibilityEnum.PRIVATE)) {
            throw new AuthorizationDeniedException("You are not authorized to access this model’s metrics");
        }

        String metricsUrl = model.getMetricsUrl();
        if (metricsUrl == null || metricsUrl.isBlank()) {
            throw new FileProcessingException("❌ Model does not contain metrics URL", null);
        }

        String key = minioService.extractMinioKey(metricsUrl);
        String bucket = bucketResolver.resolve(BucketTypeEnum.METRICS);
        byte[] content = minioService.downloadObjectAsBytes(bucket, key);

        return new ByteArrayResource(content);
    }

    public EvaluationResult evaluateClassifier(Classifier cls, Instances train, Instances test) throws Exception {
        Evaluation eval = new Evaluation(train);
        eval.evaluateModel(cls, test);

        double[][] cmatrix = eval.confusionMatrix();
        List<List<Integer>> matrix = new ArrayList<>();
        for (double[] row : cmatrix) {
            List<Integer> rowList = new ArrayList<>();
            for (double val : row) {
                rowList.add((int) val);
            }
            matrix.add(rowList);
        }

        List<String> labels = new ArrayList<>();
        for (int i = 0; i < train.classAttribute().numValues(); i++) {
            labels.add(train.classAttribute().value(i));
        }

        return new EvaluationResult(
                String.format("%.2f%%", eval.pctCorrect()),
                String.format("%.2f%%", eval.weightedPrecision() * 100),
                String.format("%.2f%%", eval.weightedRecall() * 100),
                String.format("%.2f%%", eval.weightedFMeasure() * 100),
                eval.toSummaryString(),
                matrix,
                labels
        );
    }

    public RegressionEvaluationResult evaluateRegressor(Classifier regressor, Instances train, Instances test) throws Exception {
        Evaluation eval = new Evaluation(train);
        eval.evaluateModel(regressor, test);

        List<Double> actual = new ArrayList<>();
        List<Double> predicted = new ArrayList<>();

        for (int i = 0; i < test.numInstances(); i++) {
            Instance inst = test.instance(i);
            double pred = regressor.classifyInstance(inst);
            double actualValue = inst.classValue();

            actual.add(actualValue);
            predicted.add(pred);
        }

        return new RegressionEvaluationResult(
                eval.rootMeanSquaredError(),
                eval.meanAbsoluteError(),
                Math.pow(eval.correlationCoefficient(), 2),
                eval.toSummaryString(),
                actual,
                predicted
        );
    }

    public ClusterEvaluationResult evaluateClusterer(Clusterer clusterer, Instances data) throws Exception {
        ClusterEvaluation eval = new ClusterEvaluation();
        eval.setClusterer(clusterer);
        eval.evaluateClusterer(data);

        int numClusters = eval.getNumClusters();
        double logLikelihood = eval.getLogLikelihood();

        String[] assignments = new String[data.numInstances()];
        for (int i = 0; i < data.numInstances(); i++) {
            assignments[i] = "Instance " + i + " assigned to cluster " + clusterer.clusterInstance(data.instance(i));
        }

        List<Point2D> projection2D = new ArrayList<>();
        try {
            Instances dataWithoutClass = new Instances(data);
            if (dataWithoutClass.classIndex() != -1) {
                dataWithoutClass.setClassIndex(-1);
                dataWithoutClass.deleteAttributeAt(data.classIndex());
            }

            PrincipalComponents pcaFilter = new PrincipalComponents();
            pcaFilter.setMaximumAttributes(2);
            pcaFilter.setCenterData(true);
            pcaFilter.setVarianceCovered(1.0);
            pcaFilter.setInputFormat(dataWithoutClass);

            Instances projected = Filter.useFilter(dataWithoutClass, pcaFilter);

            for (int i = 0; i < projected.numInstances(); i++) {
                double x = projected.instance(i).value(0);
                double y = projected.instance(i).value(1);
                projection2D.add(new Point2D.Double(x, y));
            }
        } catch (Exception projectionError) {
            log.warn("⚠️ PCA projection failed; proceeding without 2D coordinates", projectionError);
            projection2D.clear();
        }

        return new ClusterEvaluationResult(
                numClusters,
                logLikelihood,
                assignments,
                eval.clusterResultsToString(),
                projection2D  // 👈 Add this new field to your DTO
        );
    }

    public String generateMinioUrl(String bucket, String objectKey) {
        return minioUrl + "/" + bucket + "/" + objectKey;
    }


    public Object loadModel(String modelMinioUri) throws Exception {
        logger.info("Loading model from minio URI: {}", modelMinioUri);

        URI modelUri = new URI(modelMinioUri);

        // Splitting the path to get bucket and object names
        String[] pathParts = modelUri.getPath().split("/");
        logger.info("Path parts: {}", (Object) pathParts);

        if (pathParts.length < 2) {
            throw new RuntimeException("Invalid model URI: " + modelUri);
        }

        String minioUrl = pathParts[0];
        String bucketName = pathParts[1];

        String objectName = String.join("/", Arrays.copyOfRange(pathParts, 2, pathParts.length));


        try {

            Object model = minioService.loadObject(bucketResolver.resolve(BucketTypeEnum.MODEL), objectName);
            logger.info("Model loaded successfully");

            return model;
        } catch (Exception e) {
            String errorMessage = "Unexpected error while loading model: " + e.getMessage();
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        }
    }

    public ByteArrayResource downloadModel(Integer trainingId, User user) {
        Training training = trainingRepository.findById(trainingId).orElseThrow(() -> new EntityNotFoundException("Training not found"));
        if (!training.getUser().getUsername().equals(user.getUsername())) {
            throw new AuthorizationDeniedException("Access denied");
        }

        if (!training.getStatus().getName().equals(TrainingStatusEnum.COMPLETED)) {
            throw new IllegalStateException("Training not completed");
        }

        String modelUrl = training.getModel().getModelUrl();
        String bucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
        String key = minioService.extractMinioKey(modelUrl);
        byte[] data = minioService.downloadObjectAsBytes(bucket, key);
        return new ByteArrayResource(data);
    }

    public List<com.cloud_ml_app_thesis.dto.model.ModelDTO> getAccessibleModels(User user) {
        List<Model> models = modelRepository.findAllAccessibleToUser(user.getId());
        return models.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<com.cloud_ml_app_thesis.dto.model.ModelDTO> getAllModels() {
        List<Model> models = modelRepository.findAll();
        return models.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private com.cloud_ml_app_thesis.dto.model.ModelDTO mapToDTO(Model model) {
        Training training = model.getTraining();

        String algorithmName = null;
        String algorithmType = null;
        if (training.getAlgorithmConfiguration() != null) {
            algorithmName = training.getAlgorithmConfiguration().getAlgorithm().getName();
            algorithmType = training.getAlgorithmConfiguration().getAlgorithmType().getName().toString();
        } else if (training.getCustomAlgorithmConfiguration() != null) {
            algorithmName = training.getCustomAlgorithmConfiguration().getAlgorithm().getName();
            // Custom algorithms don't have an algorithm type specified
        }

        String datasetName = training.getDatasetConfiguration() != null
            ? training.getDatasetConfiguration().getDataset().getOriginalFileName()
            : null;

        Set<String> keywords = model.getKeywords() != null
            ? new HashSet<>(model.getKeywords())
            : Set.of();

        return com.cloud_ml_app_thesis.dto.model.ModelDTO.builder()
                .id(model.getId())
                .name(model.getName())
                .description(model.getDescription())
                .dataDescription(model.getDataDescription())
                .trainingId(training.getId())
                .algorithmName(algorithmName)
                .datasetName(datasetName)
                .modelType(model.getModelType().getName().toString())
                .algorithmType(algorithmType)
                .status(model.getStatus().getName().toString())
                .accessibility(model.getAccessibility().getName().toString())
                .categoryName(model.getCategory() != null ? model.getCategory().getName() : null)
                .categoryId(model.getCategory() != null ? model.getCategory().getId() : null)
                .keywords(keywords)
                .createdAt(model.getCreatedAt())
                .finalizationDate(model.getFinalizationDate())
                .finalized(model.isFinalized())
                .ownerUsername(training.getUser().getUsername())
                .build();
    }

    public com.cloud_ml_app_thesis.dto.model.ModelDTO getModelById(Integer modelId, User user) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        boolean isOwner = model.getTraining().getUser().getUsername().equals(user.getUsername());
        boolean isPublic = model.getAccessibility().getName().equals(ModelAccessibilityEnum.PUBLIC);

        if (!isOwner && !isPublic) {
            throw new AuthorizationDeniedException("You are not authorized to view this model");
        }

        return mapToDTO(model);
    }

    @Transactional
    public void updateModel(Integer modelId, com.cloud_ml_app_thesis.dto.request.model.ModelUpdateRequest request, User user) {
        log.info("🔄 Updating model with ID={} by user={}", modelId, user.getUsername());

        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        if (!model.getTraining().getUser().getUsername().equals(user.getUsername())) {
            throw new AccessDeniedException("You do not own this model");
        }

        if (!model.isFinalized()) {
            throw new IllegalStateException("Cannot update a non-finalized model");
        }

        if (request.getName() != null) {
            model.setName(request.getName());
        }
        if (request.getDescription() != null) {
            model.setDescription(request.getDescription());
        }
        if (request.getDataDescription() != null) {
            model.setDataDescription(request.getDataDescription());
        }
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Category not found"));
            model.setCategory(category);
        }

        ModelAccessibilityEnum accessEnum = request.isPublic() ? ModelAccessibilityEnum.PUBLIC : ModelAccessibilityEnum.PRIVATE;
        ModelAccessibility accessibility = accessibilityRepository.findByName(accessEnum)
                .orElseThrow(() -> new IllegalArgumentException("Invalid accessibility: " + accessEnum));
        model.setAccessibility(accessibility);

        if (request.getKeywords() != null) {
            model.setKeywords(new ArrayList<>(request.getKeywords()));
        }

        modelRepository.save(model);
        log.info("✅ Model with ID={} updated successfully", modelId);
    }

    @Transactional
    public void updateModelContent(Integer modelId, Integer newTrainingId, User user) {
        log.info("🔄 Updating model content: modelId={}, newTrainingId={}, user={}",
                modelId, newTrainingId, user.getUsername());

        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        // Authorization: Only owner can change content
        if (!model.getTraining().getUser().getUsername().equals(user.getUsername())) {
            log.warn("⛔ Access denied: User {} tried to update model {} owned by {}",
                    user.getUsername(), modelId, model.getTraining().getUser().getUsername());
            throw new AccessDeniedException("You do not own this model");
        }

        Training newTraining = trainingRepository.findById(newTrainingId)
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));

        // Verify new training belongs to same user
        if (!newTraining.getUser().getUsername().equals(user.getUsername())) {
            log.warn("⛔ Access denied: User {} tried to use training {} owned by {}",
                    user.getUsername(), newTrainingId, newTraining.getUser().getUsername());
            throw new AccessDeniedException("You do not own this training");
        }

        // Check if training is completed
        if (!newTraining.getStatus().getName().name().equals("COMPLETED")
                && !newTraining.getStatus().getName().name().equals("FAILED")) {
            log.warn("⛔ Training {} is not completed (status: {})",
                    newTrainingId, newTraining.getStatus().getName());
            throw new IllegalStateException("Cannot use training that is not completed");
        }

        // Check if new training already has a model
        if (newTraining.getModel() != null && !newTraining.getModel().getId().equals(modelId)) {
            log.warn("⛔ Training {} is already associated with model {}",
                    newTrainingId, newTraining.getModel().getId());
            throw new IllegalStateException("Training is already associated with another model");
        }

        Training oldTraining = model.getTraining();
        log.info("📦 Replacing model {} training: {} → {}", modelId, oldTraining.getId(), newTrainingId);

        model.setTraining(newTraining);
        model.setCreatedAt(ZonedDateTime.now());

        modelRepository.save(model);
        log.info("✅ Model content updated successfully: modelId={}, newTrainingId={}", modelId, newTrainingId);
    }

    @Transactional
    public void deleteModel(Integer modelId, User user) {
        log.info("🗑️ Deleting model with ID={} by user={}", modelId, user.getUsername());

        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        if (!model.getTraining().getUser().getUsername().equals(user.getUsername())) {
            throw new AccessDeniedException("You do not own this model");
        }

        modelRepository.delete(model);
        log.info("✅ Model with ID={} deleted successfully", modelId);
    }

    public List<ModelDTO> searchModels(ModelSearchRequest request, User user) {

        // Check if user has ADMIN role
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName().name().equals("ADMIN"));

        // Admin sees ALL algorithms, regular users see only their own + public ones
        List<Model> models = isAdmin
                ? modelRepository.findAll()
                : modelRepository.findAllAccessibleToUser(user.getId());

        log.info("Search Models: {} (isAdmin: {})", models.size(), isAdmin);

        return models.stream()
                .filter(model -> matchCriteria(model, request))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public boolean matchCriteria(Model model, ModelSearchRequest request) {
        List<Boolean> matches = new ArrayList<>();

        //Simple search
        if(StringUtils.isNotBlank(request.getSimpleSearchInput()) && !request.getSimpleSearchInput().equalsIgnoreCase("string")) {
            String searchInput = request.getSimpleSearchInput().toLowerCase();
            boolean searchMatch = model.getName() != null && model.getName().toLowerCase().contains(searchInput)
                    || (model.getDescription() != null && model.getDescription().toLowerCase().contains(searchInput))
                    || (model.getKeywords() != null && model.getKeywords().stream().anyMatch(k -> k.toLowerCase().contains(searchInput)))
                    || (model.getAccessibility().getName().name().toLowerCase().equals(searchInput))
                    || (model.getCategory().getName().toLowerCase().contains(searchInput));
            matches.add(searchMatch);
        }

        // Advanced Search - name AND description
        if(StringUtils.isNotBlank(request.getName()) && !request.getName().equalsIgnoreCase("string")) {
            boolean nameMatch = model.getName() != null && model.getName().toLowerCase().contains(request.getName().toLowerCase());
            matches.add(nameMatch);
            log.info("  ➜ Name '{}' match: {}", request.getName(), nameMatch);
        }

        if(StringUtils.isNotBlank(request.getDescription()) && !request.getDescription().equalsIgnoreCase("string")) {
            boolean descMatch = model.getDescription() != null &&
                    model.getDescription().toLowerCase().contains(request.getDescription().toLowerCase());
            matches.add(descMatch);
            log.info("  ➜ Description '{}' match: {}", request.getDescription(), descMatch);
        }

        if(StringUtils.isNotBlank(request.getCategory()) && !request.getCategory().equalsIgnoreCase("string")) {
            boolean categoryMatch = model.getCategory() != null &&
                    model.getCategory().getName().toLowerCase().contains(request.getCategory().toLowerCase());
            matches.add(categoryMatch);
            log.info("  ➜ Category '{}' match: {}", request.getCategory(), categoryMatch);
        }

        // Date range filter - combine FROM and TO with AND logic
        boolean hasDateFrom = StringUtils.isNotBlank(request.getCreatedAtFrom()) && !request.getCreatedAtFrom().equalsIgnoreCase("string");
        boolean hasDateTo = StringUtils.isNotBlank(request.getCreatedAtTo()) && !request.getCreatedAtTo().equalsIgnoreCase("string");

        if(hasDateFrom || hasDateTo) {
            if(model.getCreatedAt() == null) {
                // If createdAt is null, date filter always fails
                matches.add(false);
                log.info("  ➜ Date range match: false (model createdAt is null)");
            } else {
                LocalDateTime createdAt = model.getCreatedAt().toLocalDateTime();
                boolean dateMatch = true;

                // Check FROM date
                if(hasDateFrom) {
                    try {
                        LocalDateTime from = dateUtil.parseDateTime(request.getCreatedAtFrom(), true);
                        boolean fromMatch = createdAt.isAfter(from) || createdAt.isEqual(from);
                        dateMatch = dateMatch && fromMatch;
                        log.info("  ➜ CreatedAtFrom '{}' match: {} (model date: {})",
                                request.getCreatedAtFrom(), fromMatch, model.getCreatedAt());
                    } catch (DateTimeParseException e) {
                        log.warn("Invalid createdAtFrom format: {}", request.getCreatedAtFrom(), e);
                        dateMatch = false;
                    }
                }

                // Check TO date
                if(hasDateTo) {
                    try {
                        LocalDateTime to = dateUtil.parseDateTime(request.getCreatedAtTo(), true);
                        boolean toMatch = createdAt.isBefore(to) || createdAt.isEqual(to);
                        dateMatch = dateMatch && toMatch;
                        log.info("  ➜ CreatedAtTo '{}' match: {} (model date: {})",
                                request.getCreatedAtTo(), toMatch, model.getCreatedAt());
                    } catch (DateTimeParseException e) {
                        log.warn("Invalid createdAtTo format: {}", request.getCreatedAtTo(), e);
                        dateMatch = false;
                    }
                }

                matches.add(dateMatch);
                log.info("  ➜ Date range overall match: {}", dateMatch);
            }
        }

        if(matches.isEmpty()){
            return true;
        }

        log.info("matches found: {}",matches.stream().allMatch(Boolean::booleanValue));

        return request.getSearchMode() == ModelSearchRequest.SearchMode.AND
                ? matches.stream().allMatch(Boolean::booleanValue)
                : matches.stream().anyMatch(Boolean::booleanValue);
    }

}
