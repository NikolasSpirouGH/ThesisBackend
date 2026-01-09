package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.dataset.DatasetSelectTableDTO;
import com.cloud_ml_app_thesis.dto.request.dataset.DatasetCreateRequest;
import com.cloud_ml_app_thesis.dto.request.dataset.DatasetSearchRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.entity.Category;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.accessibility.DatasetAccessibility;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.DatasetFunctionalTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.exception.MinioFileUploadException;

import com.cloud_ml_app_thesis.repository.CategoryRepository;
import com.cloud_ml_app_thesis.repository.DatasetConfigurationRepository;
import com.cloud_ml_app_thesis.repository.accessibility.DatasetAccessibilityRepository;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.TrainingRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.specification.DatasetSpecification;
import com.cloud_ml_app_thesis.util.AlgorithmUtil;
import com.cloud_ml_app_thesis.util.DatasetUtil;
import com.cloud_ml_app_thesis.util.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.cloud_ml_app_thesis.exception.FileProcessingException;

import static com.cloud_ml_app_thesis.util.DatasetUtil.resolveDatasetMinioInfo;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetService {

    private final MinioService minioService;
    private final BucketResolver bucketResolver;
    private final CategoryService categoryService;

    private final DatasetRepository datasetRepository;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final TrainingRepository trainingRepository;
    private final UserRepository userRepository;
    private final DatasetAccessibilityRepository datasetAccessibilityRepository;
    private final CategoryRepository categoryRepository;

    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(DatasetService.class);

    @Value("${dataset.default-category-id}")
    private Integer defaultCategoryId;


    public Page<Dataset> searchDatasets(DatasetSearchRequest request, int page, int size, String sortBy, String sortDirection) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName());
        String username = isAuthenticated ? authentication.getName() : null;

        boolean hasExplicitAccess = isAuthenticated && authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ADMIN") || auth.getAuthority().equals("DATASET_MANAGER"));

        // Enforce validation rules before executing query
        if (!isAuthenticated) {
            // Unauthenticated users **MUST request only public datasets**
            if (request.getIsPublic() == null || !request.getIsPublic()) {
                throw new AuthorizationDeniedException("You can see only public datasets. For further access, please login or register.");
            }
        } else if (!hasExplicitAccess) {
            // Regular users **CANNOT search for private datasets of other users**
            if (request.getIsPublic() == null || !request.getIsPublic()) {
                if (request.getOwnerUsername() != null && !request.getOwnerUsername().equals(username)) {
                    throw new AuthorizationDeniedException("You cannot search for private datasets of other users.");
                }
                request.setOwnerUsername(username); // Restrict private dataset search to the authenticated user
            }
        }
        //  Fetch category IDs for deep search (if enabled)
        Set<Integer> categoryIds = request.getCategoryId() != null
                ? categoryService.getChildCategoryIds(request.getCategoryId(), request.getIncludeChildCategories() != null && request.getIncludeChildCategories())
                : null;

        Specification<Dataset> spec = DatasetSpecification.getDatasetsByCriteria(request, categoryIds);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.fromString(sortDirection), sortBy)
        );

        return datasetRepository.findAll(spec, pageable);
    }

    public GenericResponse<Dataset> uploadDataset(MultipartFile file, User user, DatasetFunctionalTypeEnum datasetFunctionalTypeEnum) {

        log.info("Uploading dataset with original file name: {}", file.getOriginalFilename());
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }

        String objectName = FileUtil.generateUniqueFilename(originalFilename, user.getUsername());
        log.info("Uploading dataset with object name: {}", objectName);
        String bucketName = null;
        try {
            bucketName = null;
            if (datasetFunctionalTypeEnum == DatasetFunctionalTypeEnum.TRAIN) {
                bucketName = bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET);
            } else if (datasetFunctionalTypeEnum == DatasetFunctionalTypeEnum.PREDICT) {
                bucketName = bucketResolver.resolve(BucketTypeEnum.PREDICT_DATASET);
            }
            minioService.uploadObjectToBucket(file, bucketName, objectName);

        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        Dataset dataset = new Dataset();
        dataset.setUser(user);
        dataset.setOriginalFileName(originalFilename);
        dataset.setFileName(objectName);
        dataset.setFilePath(bucketName + "/" + objectName);
        dataset.setFileSize(file.getSize());
        dataset.setContentType(file.getContentType());
        datasetAccessibilityRepository.findAll().forEach(a -> System.out.println("👀 " + a.getName()));

        DatasetAccessibility privateAccessibility = datasetAccessibilityRepository.findByName(DatasetAccessibilityEnum.PRIVATE).orElseThrow(() -> new EntityNotFoundException("Could not find PRIVATE dataset accessibility"));
        dataset.setAccessibility(privateAccessibility);
        dataset.setUploadDate(ZonedDateTime.now(ZoneId.of("Europe/Athens")));

        if (dataset.getCategory() == null) {
            Category category = categoryRepository.findById(defaultCategoryId)
                    .orElseThrow(() -> new EntityNotFoundException("Default category not found"));
            dataset.setCategory(category);
        }
        try {
            dataset = datasetRepository.save(dataset);
            return new GenericResponse<>(dataset, null, "Dataset uploaded successfully", null);

        } catch (DataAccessException e) {
            logger.error("Failed to save Dataset '{}' for user '{}'.", dataset.getOriginalFileName(), user.getUsername());
            throw e;
        }
    }

    public String uploadPredictionFile(MultipartFile file, User user) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        String uniqueFilename = FileUtil.generateUniqueFilename(originalFilename, user.getUsername());
        String bucket = bucketResolver.resolve(BucketTypeEnum.PREDICT_DATASET);

        try {
            log.info("📤 Uploading prediction input file to MinIO [bucket={}, key={}]", bucket, uniqueFilename);
            minioService.uploadObjectToBucket(file, bucket, uniqueFilename);
            return uniqueFilename;
        } catch (IOException e) {
            throw new RuntimeException("I/O error during MinIO upload", e);
        }
    }


    //*********************************************************************************************************************
    public GenericResponse<?> getDatasets(String username) {
        List<Dataset> datasets;

        // If username is null (admin/manager), return all datasets
        if (username == null) {
            datasets = datasetRepository.findAll();
        } else {
            // For regular users: return their own datasets + public datasets from others
            datasets = datasetRepository.findAccessibleDatasetsByUsername(username);
        }

        if (datasets != null && !datasets.isEmpty()) {
            List<DatasetSelectTableDTO> datasetSelectTableDTOS = datasets.stream()
                    .map(this::convertToDTO)
                    .toList();
            return new GenericResponse<List<DatasetSelectTableDTO>>(datasetSelectTableDTOS, null, null, new Metadata());
        }
        return new GenericResponse<List<DatasetSelectTableDTO>>(List.of(), null, null, new Metadata());
    }

    private DatasetSelectTableDTO convertToDTO(Dataset dataset) {
        DatasetSelectTableDTO dto = objectMapper.convertValue(dataset, DatasetSelectTableDTO.class);

        // Explicitly map accessibility entity's name field to DTO's status field
        if (dataset.getAccessibility() != null) {
            dto.setStatus(dataset.getAccessibility().getName());
        }

        // Set owner username
        if (dataset.getUser() != null) {
            dto.setOwnerUsername(dataset.getUser().getUsername());
        }

        long completeCount = trainingRepository.countByDatasetConfigurationDatasetIdAndStatus(dataset.getId(), TrainingStatusEnum.COMPLETED);
        dto.setCompleteTrainingCount(completeCount);

        long failedCount = trainingRepository.countByDatasetConfigurationDatasetIdAndStatus(dataset.getId(), TrainingStatusEnum.FAILED);
        dto.setFailedTrainingCount(failedCount);

        return dto;
    }

//*********************************************************************************************************************

    public GenericResponse<List<String>> getDatasetUrls(String email) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            //TODO LOGGER AND EXCEPTION HANDLING
            System.out.println("User do not exists");
        }
        List<String> datasetUrls = user.map(u -> u.getDatasets().stream()
                        .map(Dataset::getFilePath)
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
        return new GenericResponse<List<String>>(datasetUrls, null, null, new Metadata());
    }


    public Instances loadPredictionInstancesFromCsv(Path csvPath,
                                                    DatasetConfiguration config,
                                                    AlgorithmType algoType) throws Exception {
        log.info("📥 [SIMPLE] Loading prediction dataset from CSV: {}", csvPath);

        // 1. Μετατροπή σε ARFF
        String arffPath = DatasetUtil.csvToArff(Files.newInputStream(csvPath), csvPath.getFileName().toString());
        Instances data = new ConverterUtils.DataSource(arffPath).getDataSet();
        log.info("✅ Loaded ARFF with {} instances and {} attributes", data.numInstances(), data.numAttributes());

        // 2. Αν είναι classification ➤ inject nominal class column
        if (algoType.getName() == AlgorithmTypeEnum.CLASSIFICATION) {
            String classAttrName = DatasetUtil.resolveClassAttributeName(config, data);
            Attribute classAttr = data.attribute(classAttrName);

            if (classAttr != null && DatasetUtil.hasMissingValues(data, classAttr.index())) {
                log.info("🎯 Set class index to: {} ({})", classAttr.index(), classAttr.name());

                if (!classAttr.isNominal()) {
                    log.warn("⚠️ Class attribute is not nominal. Attempting to inject nominal class labels...");

                    // ➕ Ανάκτηση class labels από το training dataset στο MinIO
                    String[] pathParts = DatasetUtil.resolveDatasetMinioInfo(config.getDataset());
                    String bucket = pathParts[0];
                    String objectName = pathParts[1];

                    try (InputStream trainingStream = minioService.loadObjectAsInputStream(bucket, objectName)) {
                        Instances trainingData = DatasetUtil.loadDatasetInstancesByDatasetConfigurationFromMinio(config, trainingStream, objectName);
                        Attribute trainingClassAttr = trainingData.classAttribute();
                        if (trainingClassAttr == null) {
                            throw new IllegalStateException("Training dataset does not contain a class attribute");
                        }
                        if (!trainingClassAttr.isNominal()) {
                            log.warn("⚠️ Training class attribute is not nominal. Skipping nominal injection.");
                            // Do not inject anything. Just return as-is.
                            return DatasetUtil.selectColumns(
                                    data,
                                    config.getBasicAttributesColumns(),
                                    config.getTargetColumn(),
                                    1
                            );
                        }

                        List<String> classValuesFromTraining = Collections.list(trainingClassAttr.enumerateValues())
                                .stream()
                                .map(Object::toString)
                                .collect(Collectors.toList());

                        data = DatasetUtil.injectNominalClassFromTraining(data, classAttrName, classValuesFromTraining);
                        log.info("✅ Nominal class attribute injected successfully");
                    }
                }
            } else {
                log.warn("⚠️ Class attribute '{}' not found or no missing values.", classAttrName);
            }
        } else if (algoType.getName() == AlgorithmTypeEnum.REGRESSION) {
            // For REGRESSION: Add a dummy numeric target column if it doesn't exist
            log.info("📈 REGRESSION detected. Checking if target column exists in prediction data...");

            // Get target column name from TRAINING dataset (not prediction data)
            String[] pathParts = DatasetUtil.resolveDatasetMinioInfo(config.getDataset());
            String bucket = pathParts[0];
            String objectName = pathParts[1];

            String classAttrName;
            try (InputStream trainingStream = minioService.loadObjectAsInputStream(bucket, objectName)) {
                Instances trainingData = DatasetUtil.loadDatasetInstancesByDatasetConfigurationFromMinio(config, trainingStream, objectName);
                classAttrName = trainingData.classAttribute().name();
                log.info("📋 Target column from training data: '{}'", classAttrName);
            }

            Attribute classAttr = data.attribute(classAttrName);

            if (classAttr == null) {
                // Target column missing - need to add it as a dummy numeric attribute
                log.info("⚠️ Target column '{}' not found in prediction data. Adding dummy numeric column...", classAttrName);

                // Add a new numeric attribute with missing values
                data.insertAttributeAt(new Attribute(classAttrName), data.numAttributes());

                // Set all values to missing (?)
                int targetIndex = data.numAttributes() - 1;
                for (int i = 0; i < data.numInstances(); i++) {
                    data.instance(i).setMissing(targetIndex);
                }

                log.info("✅ Added dummy numeric target column '{}' at index {}", classAttrName, targetIndex);
            } else {
                log.info("✅ Target column '{}' already exists in prediction data", classAttrName);
            }
        }

        // 3. Επιλογή βασικών στηλών (feature columns)
        return DatasetUtil.selectColumns(
                data,
                config.getBasicAttributesColumns(),
                config.getTargetColumn(),
                1 // prediction mode
        );
    }

    public Instances loadTrainingInstances(DatasetConfiguration conf) throws Exception {
        // Αν έχεις ήδη κάτι αντίστοιχο, χρησιμοποίησέ το — αυτό είναι safe default.
        String[] minioInfo = DatasetUtil.resolveDatasetMinioInfo(conf.getDataset());
        String bucket = minioInfo[0];
        String objectName = minioInfo[1];

        try (InputStream in = minioService.loadObjectAsInputStream(bucket, objectName)) {
            return DatasetUtil.loadDatasetInstancesByDatasetConfigurationFromMinio(conf, in, objectName);
        }
    }

    public ByteArrayResource downloadDataset(Integer datasetId, User user) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new EntityNotFoundException("Dataset not found"));

        // Check if user has access to the dataset
        boolean isOwner = dataset.getUser().getUsername().equals(user.getUsername());
        boolean isPublic = dataset.getAccessibility().getName().equals(DatasetAccessibilityEnum.PUBLIC);

        if (!isOwner && !isPublic) {
            throw new AuthorizationDeniedException("You are not authorized to download this dataset");
        }

        String filePath = dataset.getFilePath();
        String bucket = bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET);

        // Handle both cases: full MinIO URL or just the key path
        String key;
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            key = minioService.extractMinioKey(filePath);
        } else {
            // If it's already just the key (e.g., "datasets/file.csv"), extract the object name
            key = filePath.contains("/") ? filePath.substring(filePath.indexOf("/") + 1) : filePath;
        }

        byte[] data = minioService.downloadObjectAsBytes(bucket, key);
        return new ByteArrayResource(data);
    }

    public void deleteDataset(Integer datasetId, User user) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new EntityNotFoundException("Dataset not found with ID: " + datasetId));

        // Check if user has permission to delete
        boolean isOwner = dataset.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName().name().equals("ADMIN"));

        if (!isOwner && !isAdmin) {
            throw new AuthorizationDeniedException("You are not authorized to delete this dataset. Only the owner or an admin can delete it.");
        }

        // Check if dataset is being used by any models or trainings
        // TODO: Add checks for models/trainings using this dataset if needed
        // For now, we'll allow deletion but log a warning

        log.info("User '{}' is deleting dataset '{}' (ID: {})", user.getUsername(), dataset.getFileName(), datasetId);

        try {
            // Delete the file from MinIO
            String filePath = dataset.getFilePath();
            String bucket = bucketResolver.resolve(BucketTypeEnum.TRAIN_DATASET);

            String key;
            if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
                key = minioService.extractMinioKey(filePath);
            } else {
                key = filePath.contains("/") ? filePath.substring(filePath.indexOf("/") + 1) : filePath;
            }

            minioService.deleteObject(bucket, key);
            log.info("Deleted dataset file from MinIO: bucket={}, key={}", bucket, key);
        } catch (Exception e) {
            log.error("Failed to delete dataset file from MinIO for dataset ID {}: {}", datasetId, e.getMessage(), e);
            // Continue with database deletion even if MinIO deletion fails
        }

        // Delete from database
        datasetRepository.delete(dataset);
        log.info("Dataset deleted successfully from database: ID={}", datasetId);
    }
}
