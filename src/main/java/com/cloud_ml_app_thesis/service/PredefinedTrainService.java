package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.train.ClusterEvaluationResult;
import com.cloud_ml_app_thesis.dto.train.EvaluationResult;
import com.cloud_ml_app_thesis.dto.train.PredefinedTrainMetadata;
import com.cloud_ml_app_thesis.dto.train.RegressionEvaluationResult;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.status.TaskStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.repository.DatasetConfigurationRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.util.AlgorithmUtil;
import com.cloud_ml_app_thesis.util.TrainingHelper;
import com.cloud_ml_app_thesis.util.FileUtil;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.Instances;
import weka.core.OptionHandler;
import weka.core.Utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredefinedTrainService {

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
                .orElseThrow(() -> new EntityNotFoundException("Training not found"));;

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
            if (isClassifier && AlgorithmUtil.isClassification(data)) {
                log.info("📊 Classification detected");

                Instances train = new Instances(data, 0, trainSize);

                Classifier cls = AlgorithmUtil.getClassifierInstance(algorithmClassName);

                String[] optionsArray = Utils.splitOptions(fixedRawOptions);                //TODO exception ??
                AlgorithmUtil.setClassifierOptions(cls, optionsArray);
                cls.buildClassifier(train);
                evaluationResult = modelService.evaluateClassifier(cls, trainData, testData);
                results = evaluationResult.getSummary();
                algorithmType = algorithmTypeRepository.findByName(AlgorithmTypeEnum.CLASSIFICATION).orElseThrow(() -> new EntityNotFoundException("AlgorithmType not found"));
                modelBytes = modelService.serializeModel(cls);

            } else if (isClassifier && AlgorithmUtil.isRegression(data)) {
                log.info("📈 Regression detected");

                Instances train = new Instances(data, 0, trainSize);

                Classifier cls = AlgorithmUtil.getClassifierInstance(algorithmClassName);

                String[] optionsArray = Utils.splitOptions(fixedRawOptions);                //TODO exception ??
                AlgorithmUtil.setClassifierOptions(cls, optionsArray);
                cls.buildClassifier(train);
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

        } catch (Exception e) {
            log.error("❌ Training failed [taskId={}]: {}", taskId, e.getMessage(), e);
            taskStatusService.taskFailed(taskId, e.getMessage());

            if (training != null) {
                training.setStatus(trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                        .orElseThrow(() -> new EntityNotFoundException("TrainingStatus FAILED not found")));
                training.setResults(e.getMessage());
                training.setFinishedDate(ZonedDateTime.now());
                trainingRepository.save(training);
            }

            throw new RuntimeException("Predefined training failed", e);
        }
    }

}
