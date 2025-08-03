package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.train.*;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.exception.UserInitiatedStopException;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.DatasetConfigurationRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.util.AlgorithmUtil;
import com.cloud_ml_app_thesis.util.FileUtil;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.Instances;
import weka.core.Utils;

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
import java.util.List;
import java.util.Optional;
import java.util.Random;

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

    @Transactional
    public void train(String taskId, User user, PredefinedTrainMetadata metadata) {
        log.info("🧠 Starting predefined training [taskId={}] for user={}", taskId, user.getUsername());

        AsyncTaskStatus task = taskStatusRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Async task tracking not found"));

        task.setStatus(TaskStatusEnum.RUNNING);
        log.info("Training running [taskId={}] for user={}", taskId, user.getUsername());
        taskStatusRepository.save(task);

        Training training = trainingRepository.findById(metadata.trainingId())
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));
        ;

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

            String fixedRawOptions = AlgorithmUtil.fixNestedOptions(config.getOptions());
            AlgorithmType algorithmType;
            log.info("➡️ Class index set to: " + data.classIndex());
            log.info("➡️ Class attribute name: " + data.classAttribute().name());
            log.info("⏳ Sleeping for 20 seconds before starting training (for manual stop testing)");
            Thread.sleep(20000);
            if (taskStatusService.stopRequested(taskId)) {
                throw new UserInitiatedStopException("User requested stop for task " + taskId);
            }
            if (isClassifier && AlgorithmUtil.isClassification(data)) {
                log.info("📊 Classification detected");

                Classifier cls = AlgorithmUtil.getClassifierInstance(algorithmClassName);
                String[] optionsArray = Utils.splitOptions(fixedRawOptions);                //TODO exception ??
                AlgorithmUtil.setClassifierOptions(cls, optionsArray);
                Thread.sleep(5000);
                log.info("🧪 Checking stop status after delay...");
                if (taskStatusService.stopRequested(taskId)) {
                    throw new UserInitiatedStopException("User requested stop for task " + taskId);
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

            training.setResults(results);
            training.setFinishedDate(ZonedDateTime.now());
            trainingRepository.save(training);
            modelService.saveModel(training, modelUrl, metricsUrl, predefined, user);
            Model model = modelRepository.findByTraining(training)
                    .orElseThrow(() -> new EntityNotFoundException("Model not linked to training"));
            TrainingStatus completed = trainingStatusRepository.findByName(TrainingStatusEnum.COMPLETED)
                    .orElseThrow(() -> new IllegalStateException("TrainingStatus COMPLETED not found"));

            training.setModel(model);
            training.setStatus(completed);
            trainingRepository.save(training);
            taskStatusService.completeTask(taskId);
            task.setModelId(model.getId());
            task.setTrainingId(training.getId());
            taskStatusRepository.save(task);

            log.info("✅ Training complete [taskId={}] with modelId={}", taskId, model.getId());

        }catch(UserInitiatedStopException e) {
            log.warn("🛑 Training manually stopped by user [taskId={}]: {}", taskId, e.getMessage());
            taskStatusService.taskStopped(taskId);
            task.setTrainingId(training.getId());

                training.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                        .orElseThrow(() -> new EntityNotFoundException("TrainingStatus FAILED not found")));
                training.setResults(e.getMessage());
                training.setFinishedDate(ZonedDateTime.now());
                trainingRepository.save(training);
        } catch (Exception e) {
            log.error("❌ Training failed [taskId={}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, e.getMessage());
            task.setTrainingId(training.getId());
                training.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                        .orElseThrow(() -> new EntityNotFoundException("TrainingStatus FAILED not found")));
                training.setResults(e.getMessage());
                training.setFinishedDate(ZonedDateTime.now());
                trainingRepository.save(training);

            throw new RuntimeException("Predefined training failed", e);
        }
    }

    public List<TrainingDTO> findTrainingsForUser(User user, LocalDate startDate, Integer algorithmId, ModelTypeEnum type) {
        List<Training> trainings;

        ZonedDateTime from = startDate != null
                ? startDate.atStartOfDay(ZoneId.of("Europe/Athens"))
                : null;

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

        }
        else if (from == null && algorithmId == null && type != null) {
            trainings = (type == ModelTypeEnum.CUSTOM)
                    ? trainingRepository.findAllCustom(user)
                    : trainingRepository.findAllPredefined(user);
        }
        else if (from != null && algorithmId == null && type != null) {
            trainings = (type == ModelTypeEnum.CUSTOM)
                    ? trainingRepository.findAllFromDateAndCustom(user, from)
                    : trainingRepository.findAllFromDateAndPredefined(user, from);
        }
        else {
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
                                .orElse("Unknown")
                ))
                .toList();
    }

}
