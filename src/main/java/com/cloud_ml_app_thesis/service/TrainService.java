package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.AlgorithmType;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
import com.cloud_ml_app_thesis.enumeration.accessibility.DatasetAccessibilityEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.dto.request.training.*;
import com.cloud_ml_app_thesis.dto.response.*;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.*;
import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.AlgorithmTypeRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
import com.cloud_ml_app_thesis.util.AlgorithmUtil;
import com.cloud_ml_app_thesis.util.ValidationUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import weka.core.Instances;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class TrainService {

    private final TrainingRequestHelperService trainingRequestHelperService;
    private final TrainingRepository trainingRepository;

    private final UserRepository userRepository;

    private final AlgorithmConfigurationRepository algorithmConfigurationRepository;

    private final AlgorithmRepository algorithmRepository;

    private final TrainingStatusRepository trainingStatusRepository;

    private final DatasetRepository datasetRepository;

    private final DatasetConfigurationRepository datasetConfigurationRepository;

    private final ModelService modelService;

    private final MinioService minioService;

    private final BucketResolver bucketResolver;

    private final DatasetService datasetService;

    private final AlgorithmTypeRepository algorithmTypeRepository;

    private final ObjectMapper objectMapper;

    private final ModelRepository modelRepository;


    private static final Logger logger = LoggerFactory.getLogger(TrainService.class);

    private AlgorithmConfiguration loadAlgorithmConfiguration(String algorithmConfigurationId, String algorithmId, String options){
        if(ValidationUtil.stringExists(algorithmConfigurationId)){
            Optional<AlgorithmConfiguration> algorithmConfigurationOptional = algorithmConfigurationRepository.findById(Integer.parseInt(algorithmConfigurationId));
            if(algorithmConfigurationOptional.isPresent()){
                return algorithmConfigurationOptional.get();
            } else{
                //TODO Throw Exception
                return null;
            }
        } else if(ValidationUtil.stringExists(algorithmId)){
           Optional<Algorithm> algorithmOptional = algorithmRepository.findById(Integer.parseInt(algorithmId));

        }
        return null;
    }


    //TODO Check if the Status is being correctly set at the correct time
    public GenericResponse<?> startTraining(TrainingStartRequest request, User user) throws Exception {


        TrainingDataInput trainingDataInput =  trainingRequestHelperService.configureTrainingDataInputByTrainCase(request, user);
        Training training = trainingDataInput.getTraining();
        TrainingStatus requestedStatus = trainingStatusRepository.findByName(TrainingStatusEnum.REQUESTED)
                .orElseThrow(()-> new EntityNotFoundException("Training REQUESTED status not found"));
        //TODO SAVE TRAINING WITH STATUS RUNNING WE SHOULD HAVE ALREADY SAVE IT WITH STATUS REQUESTED AND FAIL IN EVERY EXCEPTION
        training.setStartedDate(ZonedDateTime.now(ZoneId.of("Europe/Athens")));
        training.setStatus(requestedStatus);
        training = trainingRepository.save(training);
        train(training, trainingDataInput.getDataset(), trainingDataInput.getFilename(),trainingDataInput.getDatasetConfiguration(), trainingDataInput.getAlgorithmConfiguration(), user);

        return new GenericResponse<String>("Your model with id '"+trainingDataInput.getTraining().getId() +"' is being training!",null, null,new Metadata());

    }

    @Async
    public CompletableFuture<Void> train(Training training, Instances data, String filename, DatasetConfiguration datasetConfiguration, AlgorithmConfiguration algorithmConfiguration, User user) {
        return CompletableFuture.runAsync(() -> {


                TrainingStatus runningStatus = trainingStatusRepository.findByName(TrainingStatusEnum.RUNNING)
                        .orElseThrow(()-> new EntityNotFoundException("Training REQUESTED status not found"));
                training.setStatus(runningStatus);
                trainingRepository.save(training);

//                Instances data = DatasetUtil.prepareDataset(dataset, filename, datasetConfiguration);
                //data = datasetService.selectColumns(data, datasetConfiguration.getBasicAttributesColumns(), datasetConfiguration.getTargetColumn());
                String algorithmClassName = algorithmConfiguration.getAlgorithm().getClassName();

                boolean isClassifier = AlgorithmUtil.isClassifier(algorithmClassName);
                boolean isClusterer = AlgorithmUtil.isClusterer(algorithmClassName);

                //TODO see the save for finished date to not be that many saves
                if (isClassifier) {
                    TrainingStatus statusRunning = trainingStatusRepository.findByName(TrainingStatusEnum.RUNNING)
                            .orElseThrow(() ->  new EntityNotFoundException("Training status could not be found. Please try later."));
                    training.setStatus(statusRunning);
                    trainingRepository.save(training);

                    Classifier cls = null;
                    try {
                        cls = trainClassifier(training, algorithmClassName, algorithmConfiguration, data);
                    } catch (Exception e) {
                        handleTrainingFailure(training, e.getMessage());
                        throw new RuntimeException(e);
                    }

                    TrainingStatus statusComplete = trainingStatusRepository.findByName(TrainingStatusEnum.COMPLETED)
                            .orElseThrow(() ->  new EntityNotFoundException("Training status could not be found. Please try later."));

                    training.setStatus(statusComplete);
                    training.setFinishedDate(ZonedDateTime.now(ZoneId.of("Europe/Athens")));
                    trainingRepository.save(training);

                    try {
                        evaluateAndSaveClassifier(training, cls, data, user);
                    } catch (Exception e) {
                        handleTrainingFailure(training, e.getMessage());
                        throw new RuntimeException(e);
                    }
                } else if (isClusterer) {
                   TrainingStatus statusRunning = trainingStatusRepository.findByName(TrainingStatusEnum.RUNNING)
                            .orElseThrow(() ->  new EntityNotFoundException("Training status could not be found. Please try later."));
                    training.setStatus(statusRunning);
                    trainingRepository.save(training);
                    Clusterer clus = null;
                    try {
                        clus = trainClusterer(training, algorithmClassName, algorithmConfiguration, data);
                    } catch (Exception e) {
                        handleTrainingFailure(training, e.getMessage());
                        throw new RuntimeException(e);
                    }
                    TrainingStatus statusCompleted = trainingStatusRepository.findByName(TrainingStatusEnum.COMPLETED)
                            .orElseThrow(() ->  new EntityNotFoundException("Training status could not be found. Please try later."));
                    training.setStatus(statusCompleted);
                    ZonedDateTime finishedDate = ZonedDateTime.now(ZoneId.of("Europe/Athens"));
                    training.setFinishedDate(finishedDate);
                    trainingRepository.save(training);
                    try {
                        evaluateAndSaveClusterer(training, clus, data, user);
                    } catch (Exception e) {
                        handleTrainingFailure(training, e.getMessage());
                        throw new RuntimeException(e);
                    }
                } else {
                    logger.error("Unsupported algorithm type for algorithmClassName: {}", algorithmClassName);
                    try {
                        Clusterer clus = trainClusterer(training, algorithmClassName, algorithmConfiguration, data);
                    } catch (Exception e) {
                        handleTrainingFailure(training, e.getMessage());
                        throw new RuntimeException(e);
                    }
                    TrainingStatus statusComplete = trainingStatusRepository.findByName(TrainingStatusEnum.COMPLETED)
                            .orElseThrow(() ->  new EntityNotFoundException("Training status could not be found. Please try later."));
                    training.setStatus(statusComplete);
                    training.setFinishedDate(ZonedDateTime.now(ZoneId.of("Europe/Athens")));
                    trainingRepository.save(training);
                }

        });
    }


    private Classifier trainClassifier(Training training, String algorithmClassName, AlgorithmConfiguration algorithmConfiguration, Instances data) throws Exception {
        data.randomize(new Random(1));
        int trainSize = (int) Math.round(data.numInstances() * 0.6);
        Instances train = new Instances(data, 0, trainSize);

        Classifier cls = AlgorithmUtil.getClassifierInstance(algorithmClassName);

        String[] optionsArray = algorithmConfiguration.getOptions().split(",");
        //TODO exception ??
        AlgorithmUtil.setClassifierOptions(cls, optionsArray);

        cls.buildClassifier(train);
        return cls;
    }

    private Clusterer trainClusterer(Training training, String algorithmClassName, AlgorithmConfiguration algorithmConfiguration, Instances data) throws Exception {
        Clusterer clus = AlgorithmUtil.getClustererInstance(algorithmClassName);
        String[] optionsArray = algorithmConfiguration.getOptions().split(",");
        //TODO exception ??
        AlgorithmUtil.setClustererOptions(clus, optionsArray);

        clus.buildClusterer(data);
        return clus;
    }

    private void evaluateAndSaveClassifier(Training training, Classifier cls, Instances data, User user) throws Exception {
        data.randomize(new Random(1));
        int trainSize = (int) Math.round(data.numInstances() * 0.6);
        int testSize = data.numInstances() - trainSize;
        Instances test = new Instances(data, trainSize, testSize);

        test.setClassIndex(test.numAttributes() - 1);
        String results = modelService.evaluateClassifier(cls, test, test);
        byte[] modelObject = modelService.serializeModel(cls);

        String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
        String modelName = "model_".concat(training.getId().toString()).concat(user.getUsername()).concat("_").concat(timestamp);
        String bucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
        String modelUrl = bucket.concat(modelName);

        minioService.uploadObjectToBucket(modelObject ,bucket, modelName);

        AlgorithmType algorithmTypePredifined = algorithmTypeRepository.findByName(AlgorithmTypeEnum.PREDEFINED).orElseThrow(() -> new EntityNotFoundException("Algorithm type could not be found. Please try later."));

        modelService.saveModel(training, modelUrl, results, algorithmTypePredifined, user);

        training.setResults(results);
        trainingRepository.save(training);
    }

    private void evaluateAndSaveClusterer(Training training, Clusterer clus, Instances data, User user) throws Exception {
        String results = modelService.evaluateClusterer(clus, data);
        byte[] modelObject = modelService.serializeModel(clus);

        String timestamp = DateTimeFormatter.ofPattern("ddMMyyyyHHmmss").format(LocalDateTime.now());
        String modelName = "model_".concat(training.getId().toString()).concat(user.getUsername()).concat("_").concat(timestamp);
        String bucket = bucketResolver.resolve(BucketTypeEnum.MODEL);
        String modelUrl = bucket.concat(modelName);

        minioService.uploadObjectToBucket(modelObject ,bucket, modelName);

        AlgorithmType algorithmTypePredifined = algorithmTypeRepository.findByName(AlgorithmTypeEnum.PREDEFINED).orElseThrow(() -> new EntityNotFoundException("Algorithm type could not be found. Please try later."));
        modelService.saveModel(training, modelUrl, results, algorithmTypePredifined, user);

        training.setResults(results);
        trainingRepository.save(training);
    }

    private void handleTrainingFailure(Training training, String errorMessage) {
        logger.error("Training failed: {}", errorMessage);
        if (training != null) {

            TrainingStatus statusFailed = trainingStatusRepository.findByName(TrainingStatusEnum.FAILED)
                    .orElseThrow(() ->  new EntityNotFoundException("Training status could not be found. Please try later."));
            training.setStatus(statusFailed);
            training.setFinishedDate(ZonedDateTime.now(ZoneId.of("Europe/Athens")));
            trainingRepository.save(training);
        }
    }

    public ByteArrayResource getMetricsFile(Integer id, User user) {
        Model model = modelRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Model could not be found. Please try later."));

        boolean isPublic = false;
        boolean isOwner = model.getTraining().getUser().getUsername().equals(user.getUsername());

        if (!isPublic && !isOwner) {
            throw new AuthorizationDeniedException("You are not authorized to view this model’s metrics.");
        }

        String metricsUrl = model.getMetricsUrl();
        if (metricsUrl == null || metricsUrl.isBlank()) {
            throw new FileProcessingException("❌ Model does not contain metrics URL", null);
        }

        String metricsKey = minioService.extractMinioKey(metricsUrl);
        String bucket = bucketResolver.resolve(BucketTypeEnum.METRICS);

        byte[] content = minioService.downloadObjectAsBytes(bucket, metricsKey);

        return new ByteArrayResource(content);
    }
}
