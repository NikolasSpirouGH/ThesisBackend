package com.cloud_ml_app_thesis.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.train.ClusterEvaluationResult;
import com.cloud_ml_app_thesis.dto.train.EvaluationResult;
import com.cloud_ml_app_thesis.dto.train.PredefinedTrainMetadata;
import com.cloud_ml_app_thesis.dto.train.RegressionEvaluationResult;
import com.cloud_ml_app_thesis.dto.train.RetrainModelOptionDTO;
import com.cloud_ml_app_thesis.dto.train.RetrainTrainingDetailsDTO;
import com.cloud_ml_app_thesis.dto.train.RetrainTrainingOptionDTO;
import com.cloud_ml_app_thesis.dto.train.TrainingDTO;
import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.entity.AsyncTaskStatus;
import com.cloud_ml_app_thesis.entity.CustomAlgorithmConfiguration;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.entity.ModelType;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.exception.UserInitiatedStopException;
import com.cloud_ml_app_thesis.repository.AlgorithmConfigurationRepository;
import com.cloud_ml_app_thesis.repository.AlgorithmParameterRepository;
import com.cloud_ml_app_thesis.repository.AlgorithmTypeRepository;
import com.cloud_ml_app_thesis.repository.CustomAlgorithmRepository;
import com.cloud_ml_app_thesis.repository.DatasetConfigurationRepository;
import com.cloud_ml_app_thesis.repository.ModelTypeRepository;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.TrainingRepository;
import com.cloud_ml_app_thesis.repository.model.ModelExecutionRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.util.AlgorithmUtil;
import com.cloud_ml_app_thesis.util.FileUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.Instances;
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrainService {

    private final TrainingRepository trainingRepository;
    private final AlgorithmConfigurationRepository algorithmConfigurationRepository;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final TrainingStatusRepository trainingStatusRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final MinioService minioService;
    private final BucketResolver bucketResolver;
    private final ModelService modelService;
    private final ModelRepository modelRepository;
    private final ModelTypeRepository modelTypeRepository;
    private final TaskStatusService taskStatusService;
    private final AlgorithmTypeRepository algorithmTypeRepository;
    private final EntityManager entityManager;
    private final ModelExecutionRepository modelExecutionRepository;
    private final AlgorithmParameterRepository algorithmParameterRepository;
    private final CustomAlgorithmRepository customAlgorithmRepository;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void train(String taskId, User user, PredefinedTrainMetadata metadata) {
        AlgorithmUtil.ensureWekaClasspathCompatibility();
        boolean complete = false;
        Model model = null;
        Integer trainingId = null;
        log.info("🧠 Starting predefined training [taskId={}] for user={}", taskId, user.getUsername());

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Async task tracking not found"));

        task.setStatus(TaskStatusEnum.RUNNING);
        log.info("Training running [taskId={}] for user={}", taskId, user.getUsername());
        taskStatusRepository.save(task);

        Training training = trainingRepository.findById(metadata.trainingId())
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));

        try {
            AlgorithmConfiguration config = algorithmConfigurationRepository.findById(metadata.algorithmConfigurationId())
                    .orElseThrow(() -> new EntityNotFoundException("AlgorithmConfiguration not found"));
            DatasetConfiguration datasetConfig = datasetConfigurationRepository.findById(metadata.datasetConfigurationId())
                    .orElseThrow(() -> new EntityNotFoundException("DatasetConfiguration not found"));

            String algorithmClassName = config.getAlgorithm().getClassName();
            boolean isClassifier = AlgorithmUtil.isClassifier(algorithmClassName);
            boolean isClusterer = AlgorithmUtil.isClusterer(algorithmClassName);

            training.setAlgorithmConfiguration(config);
            training.setStartedDate(ZonedDateTime.now());
            training.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.RUNNING)
                    .orElseThrow(() -> new EntityNotFoundException("Status RUNNING not found")));
            training = trainingRepository.save(training);
            trainingId = training.getId();
            entityManager.detach(training);

            String results;
            byte[] modelBytes;
            EvaluationResult evaluationResult = null;
            RegressionEvaluationResult regressionEvaluationResult = null;
            ClusterEvaluationResult clusterEvaluationResult = null;

            Instances data = metadata.dataset();
            data.randomize(new Random(1));
            int trainSize = (int) (data.numInstances() * 0.7);
            int testSize = data.numInstances() - trainSize;
            Instances trainData = new Instances(data, 0, trainSize);
            Instances testData = new Instances(data, trainSize, testSize);

            // 🔄 Convert numeric class to nominal for classification algorithms
            if (isClassifier && data.classIndex() >= 0 && data.classAttribute().isNumeric()) {
                log.info("🔄 Converting numeric class to nominal for classification algorithm");
                log.info("   Class attribute: {} (numeric)", data.classAttribute().name());

                NumericToNominal convert = new NumericToNominal();
                String classIndexStr = String.valueOf(data.classIndex() + 1); // Weka uses 1-based indexing for filters
                convert.setAttributeIndices(classIndexStr);
                convert.setInputFormat(data);

                // Apply filter to all datasets
                data = Filter.useFilter(data, convert);
                trainData = Filter.useFilter(trainData, convert);
                testData = Filter.useFilter(testData, convert);

                log.info("✅ Converted class to nominal. Values: {}", data.classAttribute().toString());
            }

            String fixedRawOptions = AlgorithmUtil.fixNestedOptions(config.getOptions());
            AlgorithmType algorithmType;
            log.info("➡️ Class index set to: " + data.classIndex());
            log.info("➡️ Class attribute name: " + data.classAttribute().name());
            log.info("⏳ Sleeping for 20 seconds before starting training (for manual stop testing)");
            //Thread.sleep(10000);
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop for task " + taskId);
            }
            if (isClassifier && AlgorithmUtil.isClassification(data)) {
                log.info("📊 Classification detected");

                Classifier cls = AlgorithmUtil.getClassifierInstance(algorithmClassName);
                String[] optionsArray = Utils.splitOptions(fixedRawOptions);                //TODO exception ??
                AlgorithmUtil.setClassifierOptions(cls, optionsArray);
                Thread.sleep(5000);
                log.info("🧪 Checking stop status before classification training...");
                if (taskStatusService.stopRequested(taskId)) {
                    throw new UserInitiatedStopException("User requested stop before classification training for task " + taskId);
                }
                cls.buildClassifier(trainData);
                evaluationResult = modelService.evaluateClassifier(cls, trainData, testData);
                results = evaluationResult.getSummary();
                algorithmType = algorithmTypeRepository.findByName(AlgorithmTypeEnum.CLASSIFICATION).orElseThrow(() -> new EntityNotFoundException("AlgorithmType not found"));
                modelBytes = modelService.serializeModel(cls);

            } else if (isClassifier && AlgorithmUtil.isRegression(data)) {
                log.info("📈 Regression detected");
                Classifier cls = AlgorithmUtil.getClassifierInstance(algorithmClassName);
                String[] optionsArray = Utils.splitOptions(fixedRawOptions);                //TODO exception ??
                AlgorithmUtil.setClassifierOptions(cls, optionsArray);
                if (taskStatusService.stopRequested(taskId)) {
                    throw new UserInitiatedStopException("User requested stop before regression training for task " + taskId);
                }
                cls.buildClassifier(trainData);
                regressionEvaluationResult = modelService.evaluateRegressor(cls, trainData, testData);
                results = regressionEvaluationResult.getSummary();
                algorithmType = algorithmTypeRepository.findByName(AlgorithmTypeEnum.REGRESSION).orElseThrow(() -> new EntityNotFoundException("AlgorithmType not found"));
                modelBytes = modelService.serializeModel(cls);

            } else if (isClusterer) {
                log.info("🔀 Clustering detected");
                if (data.classIndex() >= 0) data.setClassIndex(-1); // Δεν πρέπει να έχει class attribute
                Clusterer cls = AlgorithmUtil.getClustererInstance(algorithmClassName);
                String[] optionsArray = Utils.splitOptions(fixedRawOptions);
                log.info("Options: {}", config.getOptions());
                log.info("✅ Parsed Weka options array: {}", Arrays.toString(optionsArray));
                AlgorithmUtil.setClustererOptions(cls, optionsArray);
                if (taskStatusService.stopRequested(taskId)) {
                    throw new UserInitiatedStopException("User requested stop before clustering training for task " + taskId);
                }
                cls.buildClusterer(data);
                clusterEvaluationResult = modelService.evaluateClusterer(cls, data);
                results = clusterEvaluationResult.getSummary();
                algorithmType = algorithmTypeRepository.findByName(AlgorithmTypeEnum.CLUSTERING).orElseThrow(() -> new EntityNotFoundException("AlgorithmType not found"));
                modelBytes = modelService.serializeModel(cls);
            } else {
                throw new UnsupportedOperationException("Unsupported algorithm type: " + algorithmClassName);
            }

            config.setAlgorithmType(algorithmType);
            algorithmConfigurationRepository.save(config);
            String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
            String modelKey = user.getUsername() + "_" + timestamp + "_model.pkl";
            String metricsKey = user.getUsername() + "_" + timestamp + "_metrics.json";

            String modelBucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
            String metricsBucket = bucketResolver.resolve(BucketTypeEnum.METRICS);

            Path metricsPath = null;
            if (evaluationResult != null) {
                metricsPath = FileUtil.writeMetricsToJsonFile(evaluationResult);
            } else if (regressionEvaluationResult != null) {
                metricsPath = FileUtil.writeMetricsToJsonFile(regressionEvaluationResult);
            } else {
                metricsPath = FileUtil.writeMetricsToJsonFile(clusterEvaluationResult);
            }

            try (InputStream modelIn = new ByteArrayInputStream(modelBytes);
                 InputStream metricsIn = Files.newInputStream(metricsPath)) {
                minioService.uploadToMinio(modelIn, modelBucket, modelKey, modelBytes.length, "application/octet-stream");
                minioService.uploadToMinio(metricsIn, metricsBucket, metricsKey, Files.size(metricsPath), "application/json");
            }

            String modelUrl = modelService.generateMinioUrl(modelBucket, modelKey);
            String metricsUrl = modelService.generateMinioUrl(metricsBucket, metricsKey);

            ModelType predefined = modelTypeRepository.findByName(ModelTypeEnum.PREDEFINED)
                    .orElseThrow(() -> new EntityNotFoundException("AlgorithmType PREDEFINED not found"));


            modelService.saveModel(training, modelUrl, metricsUrl, predefined);
            model = modelRepository.findByTraining(training)
                    .orElseThrow(() -> new EntityNotFoundException("Model not linked to training"));
           // log.info("⏳ Sleeping for 20 seconds after starting training (for manual stop testing)");
            Thread.sleep(10000);
            complete = true;
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop after model and metrics were uploaded");
            }

            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Integer finalTrainingId = trainingId;
            Model finalModel = model;
            transactionTemplate.executeWithoutResult(status -> {
                Training tr = trainingRepository.findById(finalTrainingId)
                        .orElseThrow(() -> new EntityNotFoundException("Training not found"));
                tr.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.COMPLETED)
                        .orElseThrow(() -> new EntityNotFoundException("TrainingStatus COMPLETED not found")));
                tr.setResults(results);
                tr.setModel(finalModel);
                tr.setFinishedDate(ZonedDateTime.now());
                trainingRepository.saveAndFlush(tr);
            });

            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Model finalModel1 = model;
            Integer finalTrainingId1 = trainingId;
            transactionTemplate.executeWithoutResult(status -> {
                if (task.getStatus() != TaskStatusEnum.STOPPED) {
                    taskStatusService.completeTask(taskId); // bumps version
                    AsyncTaskStatus fresh = taskStatusRepository.findById(taskId).orElseThrow();
                    fresh.setModelId(finalModel1.getId());
                    fresh.setTrainingId(finalTrainingId1);
                    taskStatusRepository.saveAndFlush(fresh);
                }
            });
            log.info("✅ Training complete [taskId={}] with modelId={}", taskId, model.getId());

        } catch (UserInitiatedStopException e) {
            log.warn("🛑 Training manually stopped by user [taskId={}]: {}", taskId, e.getMessage());

            taskStatusService.taskStoppedTraining(taskId, training.getId(), model.getId());

            // User-initiated stop should always result in FAILED status
            TrainingStatusEnum finalStatus = TrainingStatusEnum.FAILED;

            training.setStatus(trainingStatusRepository.findByName(finalStatus)
                    .orElseThrow(() -> new EntityNotFoundException("TrainingStatus completed")));
            training.setFinishedDate(ZonedDateTime.now());

            trainingRepository.save(training);
        }catch (Exception e) {
            log.error("❌ Training failed [taskId={}]: {}", taskId, e.getMessage(), e);

            taskStatusService.taskFailed(taskId, e.getMessage());

            final Integer tid = trainingId;

            if (tid != null) {
                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.executeWithoutResult(status -> {
                    Training tr = trainingRepository.findById(tid)
                            .orElseThrow(() -> new EntityNotFoundException("Training not found"));
                    tr.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                            .orElseThrow(() -> new EntityNotFoundException("TrainingStatus FAILED not found")));
                    tr.setResults(e.getMessage());
                    tr.setFinishedDate(ZonedDateTime.now());
                    trainingRepository.saveAndFlush(tr);
                });

                transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                transactionTemplate.executeWithoutResult(status -> {
                    var t = taskStatusRepository.findById(taskId).orElseThrow();
                    t.setTrainingId(tid);
                    taskStatusRepository.saveAndFlush(t);
                });
            }

            throw new RuntimeException("Predefined training failed", e);
        }
    }

    public List<TrainingDTO> findAllTrainings(LocalDate startDate, Integer algorithmId, ModelTypeEnum type) {
        // Admin version - returns all trainings with optional filtering
        List<Training> trainings = trainingRepository.findAll();

        ZonedDateTime from = startDate != null
                ? startDate.atStartOfDay(ZoneId.of("UTC"))
                : null;

        log.info("🔍 [ADMIN] Searching ALL trainings, fromDate={}, from={}, algorithmId={}, type={}",
                 startDate, from, algorithmId, type);

        // Apply filters in-memory
        return trainings.stream()
                .filter(training -> {
                    // Filter by date if provided
                    if (from != null && training.getStartedDate() != null) {
                        if (training.getStartedDate().isBefore(from)) {
                            return false;
                        }
                    }

                    // Filter by algorithmId and type if provided
                    if (algorithmId != null && type != null) {
                        if (type == ModelTypeEnum.CUSTOM) {
                            CustomAlgorithmConfiguration customConfig = training.getCustomAlgorithmConfiguration();
                            if (customConfig == null || customConfig.getAlgorithm() == null
                                || !customConfig.getAlgorithm().getId().equals(algorithmId)) {
                                return false;
                            }
                        } else if (type == ModelTypeEnum.PREDEFINED) {
                            AlgorithmConfiguration config = training.getAlgorithmConfiguration();
                            if (config == null || config.getAlgorithm() == null
                                || !config.getAlgorithm().getId().equals(algorithmId)) {
                                return false;
                            }
                        }
                    }

                    // Filter by type only if provided (without algorithmId)
                    if (algorithmId == null && type != null) {
                        if (type == ModelTypeEnum.CUSTOM) {
                            if (training.getCustomAlgorithmConfiguration() == null) {
                                return false;
                            }
                        } else if (type == ModelTypeEnum.PREDEFINED) {
                            if (training.getAlgorithmConfiguration() == null) {
                                return false;
                            }
                        }
                    }

                    return true;
                })
                .map(training -> new TrainingDTO(
                        training.getId(),
                        AlgorithmUtil.resolveAlgorithmName(training),
                        training.getModel() != null ? training.getModel().getModelType().getName().name() : null,
                        training.getStatus().getName(),
                        training.getStartedDate(),
                        training.getFinishedDate(),
                        Optional.ofNullable(training.getDatasetConfiguration())
                                .map(DatasetConfiguration::getDataset)
                                .map(Dataset::getFileName)
                                .orElse("Unknown"),
                        training.getModel() != null ? training.getModel().getId() : null,
                        training.getUser() != null ? training.getUser().getUsername() : "Unknown"
                ))
                .collect(Collectors.toList());
    }

    public List<TrainingDTO> findTrainingsForUser(User user, LocalDate startDate, Integer algorithmId, ModelTypeEnum type) {
        List<Training> trainings;

        ZonedDateTime from = startDate != null
                ? startDate.atStartOfDay(ZoneId.of("UTC"))
                : null;

        log.info("🔍 Searching trainings for user={}, fromDate={}, from={}, algorithmId={}, type={}",
                 user.getUsername(), startDate, from, algorithmId, type);

        if (from == null && algorithmId == null && type == null) {
            trainings = trainingRepository.findAllByUser(user);

        } else if (from != null && algorithmId == null && type == null) {
            trainings = trainingRepository.findAllFromDate(user, from);

        } else if (from == null && algorithmId != null && type != null) {
            trainings = (type == ModelTypeEnum.CUSTOM)
                    ? trainingRepository.findByCustomAlgorithm(user, algorithmId)
                    : trainingRepository.findByPredefinedAlgorithm(user, algorithmId);

        } else if (from != null && algorithmId != null && type != null) {
            trainings = (type == ModelTypeEnum.CUSTOM)
                    ? trainingRepository.findFromDateAndCustomAlgorithm(user, from, algorithmId)
                    : trainingRepository.findFromDateAndPredefinedAlgorithm(user, from, algorithmId);

        } else if (from == null && algorithmId == null && type != null) {
            trainings = (type == ModelTypeEnum.CUSTOM)
                    ? trainingRepository.findAllCustom(user)
                    : trainingRepository.findAllPredefined(user);
        } else if (from != null && algorithmId == null && type != null) {
            trainings = (type == ModelTypeEnum.CUSTOM)
                    ? trainingRepository.findAllFromDateAndCustom(user, from)
                    : trainingRepository.findAllFromDateAndPredefined(user, from);
        } else {
            throw new IllegalArgumentException("If algorithmId is provided, algorithmType must also be specified.");
        }

        return trainings.stream()
                .map(training -> new TrainingDTO(
                        training.getId(),
                        AlgorithmUtil.resolveAlgorithmName(training),
                        training.getModel() != null ? training.getModel().getModelType().getName().name() : null,
                        training.getStatus().getName(),
                        training.getStartedDate(),
                        training.getFinishedDate(),
                        Optional.ofNullable(training.getDatasetConfiguration())
                                .map(DatasetConfiguration::getDataset)
                                .map(Dataset::getFileName)
                                .orElse("Unknown"),
                        training.getModel() != null ? training.getModel().getId() : null,
                        training.getUser() != null ? training.getUser().getUsername() : "Unknown"
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RetrainTrainingOptionDTO> getRetrainTrainingOptions(User user) {
        Comparator<Training> byStartedDate = Comparator.comparing(
                Training::getStartedDate,
                Comparator.nullsLast(Comparator.naturalOrder()));

        List<Training> trainings = trainingRepository.findCompletedTrainingsForUser(user);

        return trainings.stream()
                .sorted(byStartedDate.reversed())
                .map(training -> new RetrainTrainingOptionDTO(
                        training.getId(),
                        AlgorithmUtil.resolveAlgorithmName(training),
                        datasetName(training),
                        training.getStatus() != null && training.getStatus().getName() != null
                                ? training.getStatus().getName().name()
                                : null,
                        training.getModel() != null ? training.getModel().getId() : null
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RetrainModelOptionDTO> getRetrainModelOptions(User user) {
        Comparator<Model> byTrainingStarted = Comparator.comparing(
                (Model model) -> {
                    Training training = model.getTraining();
                    return training != null ? training.getStartedDate() : null;
                },
                Comparator.nullsLast(Comparator.naturalOrder())
        ).reversed();

        var models = modelRepository.findAllByTraining_User(user);

        return models.stream()
                .sorted(byTrainingStarted)
                .map(model -> {
                    Training training = model.getTraining();
                    String algorithmName = training != null ? AlgorithmUtil.resolveAlgorithmName(training) : null;
                    String datasetName = training != null ? datasetName(training) : null;
                    String status = model.getStatus() != null && model.getStatus().getName() != null
                            ? model.getStatus().getName().name()
                            : null;
                    String modelName = model.getName();
                    if (modelName == null || modelName.isBlank()) {
                        modelName = "Model " + model.getId();
                    }
                    Integer trainingId = training != null ? training.getId() : null;
                    return new RetrainModelOptionDTO(
                            model.getId(),
                            modelName,
                            trainingId,
                            algorithmName,
                            datasetName,
                            status
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public RetrainTrainingDetailsDTO getRetrainTrainingDetails(Integer trainingId, User user) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));

        ensureOwnedByUser(training, user);
        return toRetrainDetails(training);
    }

    @Transactional(readOnly = true)
    public RetrainTrainingDetailsDTO getRetrainModelDetails(Integer modelId, User user) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new EntityNotFoundException("Model not found"));

        Training training = model.getTraining();
        if (training == null) {
            throw new EntityNotFoundException("No training associated with model " + modelId);
        }

        ensureOwnedByUser(training, user);
        return toRetrainDetails(training);
    }

    private void ensureOwnedByUser(Training training, User user) {
        if (isAdmin(user)) {
            return;
        }
        if (training.getUser() == null || training.getUser().getId() == null
                || user.getId() == null || !training.getUser().getId().equals(user.getId())) {
            throw new AuthorizationDeniedException("Not allowed");
        }
    }

    private String datasetName(Training training) {
        DatasetConfiguration configuration = training.getDatasetConfiguration();
        return Optional.ofNullable(configuration)
                .map(DatasetConfiguration::getDataset)
                .map(Dataset::getFileName)
                .orElse(null);
    }

    private RetrainTrainingDetailsDTO toRetrainDetails(Training training) {
        DatasetConfiguration datasetConf = training.getDatasetConfiguration();
        Dataset dataset = datasetConf != null ? datasetConf.getDataset() : null;

        AlgorithmConfiguration algorithmConf = training.getAlgorithmConfiguration();
        CustomAlgorithmConfiguration customAlgorithmConf = training.getCustomAlgorithmConfiguration();

        Integer algorithmId = algorithmConf != null && algorithmConf.getAlgorithm() != null
                ? algorithmConf.getAlgorithm().getId()
                : null;
        Integer algorithmConfigurationId = algorithmConf != null ? algorithmConf.getId() : null;
        Integer customAlgorithmConfigurationId = customAlgorithmConf != null ? customAlgorithmConf.getId() : null;
        String algorithmName = algorithmConf != null && algorithmConf.getAlgorithm() != null
                ? algorithmConf.getAlgorithm().getName()
                : customAlgorithmConf != null && customAlgorithmConf.getAlgorithm() != null
                ? customAlgorithmConf.getAlgorithm().getName()
                : null;
        String algorithmOptions = algorithmConf != null ? algorithmConf.getOptions() : null;

        Integer datasetId = dataset != null ? dataset.getId() : null;
        Integer datasetConfigurationId = datasetConf != null ? datasetConf.getId() : null;
        String basicCols = datasetConf != null ? datasetConf.getBasicAttributesColumns() : null;
        String targetCol = datasetConf != null ? datasetConf.getTargetColumn() : null;
        String status = training.getStatus() != null && training.getStatus().getName() != null
                ? training.getStatus().getName().name()
                : null;

        return new RetrainTrainingDetailsDTO(
                training.getId(),
                training.getModel() != null ? training.getModel().getId() : null,
                algorithmId,
                algorithmConfigurationId,
                customAlgorithmConfigurationId,
                algorithmName,
                algorithmOptions,
                datasetId,
                datasetConfigurationId,
                dataset != null ? dataset.getFileName() : null,
                basicCols,
                targetCol,
                status
        );
    }

    private boolean isAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> role.getName() == UserRoleEnum.ADMIN);
    }

    @Transactional
    public void deleteTrain(Integer id, User currentUser) {
        Training training = trainingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));

        log.info("Training to delete {}", training.getId());

        if (!training.getUser().getUsername().equals(currentUser.getUsername())) {
            throw new AuthorizationDeniedException("Not allowed");
        }
        // Handle running/requested trainings by stopping them first
        if (training.getStatus().getName() == TrainingStatusEnum.RUNNING
                || training.getStatus().getName() == TrainingStatusEnum.REQUESTED) {

            log.info("🛑 Training {} is running/requested - attempting to stop before deletion", training.getId());

            // Find the associated task and stop it
            Optional<AsyncTaskStatus> taskOpt = taskStatusRepository.findByTrainingId(training.getId());
            if (taskOpt.isPresent()) {
                AsyncTaskStatus task = taskOpt.get();
                try {
                    // Request stop
                    taskStatusService.stopTask(task.getTaskId(), currentUser.getUsername());

                    // Update training status to FAILED
                    TrainingStatus failedStatus = trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                            .orElseThrow(() -> new EntityNotFoundException("TrainingStatus FAILED not found"));
                    training.setStatus(failedStatus);
                    training.setFinishedDate(ZonedDateTime.now());
                    trainingRepository.save(training);

                    log.info("✅ Training {} stopped and marked as FAILED for deletion", training.getId());
                } catch (Exception e) {
                    log.warn("⚠️ Could not stop task for training {}: {}. Proceeding with force deletion.", training.getId(), e.getMessage());
                }
            } else {
                log.warn("⚠️ No task found for running training {}. Marking as FAILED for deletion.", training.getId());

                // Force mark as FAILED if no task found
                TrainingStatus failedStatus = trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                        .orElseThrow(() -> new EntityNotFoundException("TrainingStatus FAILED not found"));
                training.setStatus(failedStatus);
                training.setFinishedDate(ZonedDateTime.now());
                trainingRepository.save(training);
            }
        }

        // Prepare MinIO keys if model exists
        String modelKey = null, metricsKey = null;
        Model model = training.getModel();

        if (model != null) {
            if (model.getModelUrl() != null && !model.getModelUrl().isBlank()) {
                modelKey = minioService.extractMinioKey(model.getModelUrl());
            }
            if (model.getMetricsUrl() != null && !model.getMetricsUrl().isBlank()) {
                metricsKey = minioService.extractMinioKey(model.getMetricsUrl());
            }

            // Break relation and delete model
            model.setTraining(null);
            training.setModel(null);
        }

        trainingRepository.delete(training);

        // MinIO cleanup after commit
        final String finalModelKey = modelKey;
        final String finalMetricsKey = metricsKey;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                if (finalModelKey != null) {
                    try {
                        minioService.deleteObject(bucketResolver.resolve(BucketTypeEnum.MODEL), finalModelKey);
                    } catch (Exception ex) {
                        log.warn("MinIO delete failed for model {}", finalModelKey, ex);
                    }
                }
                if (finalMetricsKey != null) {
                    try {
                        minioService.deleteObject(bucketResolver.resolve(BucketTypeEnum.METRICS), finalMetricsKey);
                    } catch (Exception ex) {
                        log.warn("MinIO delete failed for metrics {}", finalMetricsKey, ex);
                    }
                }
            }
        });

        log.info("✅ Training {} and its model were deleted", id);
    }

    /**
     * Get algorithms that the user has actually used in their trainings
     */
    @Transactional(readOnly = true)
    public List<com.cloud_ml_app_thesis.entity.Algorithm> getUserUsedPredefinedAlgorithms(User user) {
        return trainingRepository.findDistinctPredefinedAlgorithmsByUser(user);
    }

    @Transactional(readOnly = true)
    public List<com.cloud_ml_app_thesis.entity.CustomAlgorithm> getUserUsedCustomAlgorithms(User user) {
        return trainingRepository.findDistinctCustomAlgorithmsByUser(user);
    }
}
