package com.cloud_ml_app_thesis.unit_tests.service;

//import com.cloud_ml_app_thesis.config.BucketResolver;
//import com.cloud_ml_app_thesis.dto.train.EvaluationResult;
//import com.cloud_ml_app_thesis.dto.train.PredefinedTrainMetadata;
//import com.cloud_ml_app_thesis.entity.*;
//import com.cloud_ml_app_thesis.entity.model.Model;
//import com.cloud_ml_app_thesis.entity.status.TrainingStatus;
//import com.cloud_ml_app_thesis.enumeration.AlgorithmTypeEnum;
//import com.cloud_ml_app_thesis.enumeration.ModelTypeEnum;
//import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
//import com.cloud_ml_app_thesis.repository.*;
//import com.cloud_ml_app_thesis.repository.model.ModelRepository;
//import com.cloud_ml_app_thesis.repository.status.TrainingStatusRepository;
//import com.cloud_ml_app_thesis.service.*;
//import com.cloud_ml_app_thesis.util.AlgorithmUtil;
//import com.cloud_ml_app_thesis.util.TrainingHelper;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.core.io.ClassPathResource;
//import weka.core.Instances;
//import weka.core.OptionHandler;
//import weka.core.converters.CSVLoader;
//import weka.core.converters.ConverterUtils;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileReader;
//import java.io.InputStream;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.Mockito.*;
//import static org.hamcrest.MatcherAssert.assertThat;
//import static org.hamcrest.Matchers.hasItems;
//
//@ExtendWith(MockitoExtension.class)
//class PredefinedTrainServiceTest {
//
//    @InjectMocks
//    private PredefinedTrainService predefinedTrainService;
//
//    @Mock private TrainingRepository trainingRepository;
//    @Mock private AlgorithmConfigurationRepository algorithmConfigurationRepository;
//    @Mock private DatasetConfigurationRepository datasetConfigurationRepository;
//    @Mock private TrainingStatusRepository trainingStatusRepository;
//    @Mock private TaskStatusRepository taskStatusRepository;
//    @Mock private MinioService minioService;
//    @Mock private BucketResolver bucketResolver;
//    @Mock private ModelService modelService;
//    @Mock private ModelRepository modelRepository;
//    @Mock private ModelTypeRepository modelTypeRepository;
//    @Mock private AlgorithmTypeRepository algorithmTypeRepository;
//    @Mock private TaskStatusService taskStatusService;
//
//    @Mock
//    private AlgorithmConfiguration config;
//    @Mock
//    private Algorithm algorithm;
//
//
//    @Test
//    void testTrain_classifier_configuresOptionsCorrectly() throws Exception {
//        // Arrange
//        String rawOptions = "C:0.25,M:2"; // J48 example
//        String algorithmClass = "weka.classifiers.trees.J48";
//
//        Algorithm algorithm = new Algorithm();
//        algorithm.setClassName(algorithmClass);
//
//        AlgorithmConfiguration config = new AlgorithmConfiguration();
//        config.setId(1);
//        config.setOptions(rawOptions);
//        config.setAlgorithm(algorithm);
//
//        CSVLoader loader = new CSVLoader();
//        loader.setSource(new File("src/test/resources/datasets/dataset2.csv"));
//        Instances data = loader.getDataSet();
//        data.setClassIndex(data.numAttributes() - 1); // classification
//
//        PredefinedTrainMetadata metadata = new PredefinedTrainMetadata(
//                1, data, 1, 1
//        );
//
//        User user = new User();
//        user.setUsername("testuser");
//
//        Training training = new Training();
//
//        when(trainingRepository.findById(any())).thenReturn(Optional.of(training));
//        when(trainingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
//        when(taskStatusRepository.findById(anyString())).thenReturn(Optional.of(new AsyncTaskStatus()));
//        when(trainingRepository.findById(1)).thenReturn(Optional.of(training));
//        when(algorithmConfigurationRepository.findById(1)).thenReturn(Optional.of(config));
//        when(datasetConfigurationRepository.findById(1)).thenReturn(Optional.of(new DatasetConfiguration()));
//        when(trainingStatusRepository.findByName(any())).thenReturn(Optional.of(new TrainingStatus()));
//        when(modelService.evaluateClassifier(any(), any(), any())).thenReturn(
//                new EvaluationResult("0.9", "0.9", "0.9", "0.9", "summary", List.of(), List.of())
//        );
//        when(modelService.serializeModel(any())).thenReturn(new byte[]{1, 2, 3});
//        when(bucketResolver.resolve(any())).thenReturn("bucket");
//        when(modelService.generateMinioUrl(any(), any())).thenReturn("url");
//        when(modelTypeRepository.findByName(any())).thenReturn(Optional.of(new ModelType()));
//        when(modelRepository.findByTraining(any())).thenReturn(Optional.of(new Model()));
//
//        // Act
//        predefinedTrainService.train("task1", user, metadata);
//
//        // Assert
//        ArgumentCaptor<AlgorithmConfiguration> captor = ArgumentCaptor.forClass(AlgorithmConfiguration.class);
//        verify(algorithmConfigurationRepository, atLeastOnce()).save(captor.capture());
//
//        String[] finalOptions = AlgorithmUtil.deserializeOptions(captor.getValue().getOptions());
//
//        assertTrue(Arrays.asList(finalOptions).containsAll(List.of("-C", "0.25", "-M", "2")));
//    }
//
//    @Test
//    void testTrain_randomForest_mergesOptionsCorrectly() throws Exception {
//        String options = ""; // I = numIterations, M = numFeatures
//        String className = "weka.classifiers.trees.RandomForest";
//
//        Algorithm algo = new Algorithm();
//        algo.setClassName(className);
//
//        AlgorithmConfiguration config = new AlgorithmConfiguration();
//        config.setOptions(options);
//        config.setAlgorithm(algo);
//
//        CSVLoader loader = new CSVLoader();
//        loader.setSource(new File("src/test/resources/datasets/dataset2.csv"));
//        Instances data = loader.getDataSet();        data.setClassIndex(data.numAttributes() - 1);
//
//        PredefinedTrainMetadata metadata = new PredefinedTrainMetadata(1, data, 1, 1);
//        User user = new User(); user.setUsername("testuser");
//
//        Training training = new Training();
//        training.setId(1);
//        Model model = new Model(); model.setId(42);
//
//        when(trainingRepository.findById(any())).thenReturn(Optional.of(training));
//        when(trainingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
//        when(taskStatusRepository.findById(any())).thenReturn(Optional.of(new AsyncTaskStatus()));
//        when(trainingRepository.findById(any())).thenReturn(Optional.of(training));
//        when(algorithmConfigurationRepository.findById(any())).thenReturn(Optional.of(config));
//        when(datasetConfigurationRepository.findById(any())).thenReturn(Optional.of(new DatasetConfiguration()));
//        when(trainingStatusRepository.findByName(any())).thenReturn(Optional.of(new TrainingStatus()));
//        when(modelService.evaluateClassifier(any(), any(), any()))
//                .thenReturn(new EvaluationResult("0.9", "0.8", "0.85", "0.87", "summary", List.of(), List.of()));
//        when(modelService.serializeModel(any())).thenReturn(new byte[]{1,2,3});
//        when(bucketResolver.resolve(any())).thenReturn("bucket");
//        when(modelService.generateMinioUrl(any(), any())).thenReturn("url");
//        when(modelTypeRepository.findByName(any())).thenReturn(Optional.of(new ModelType()));
//        when(modelRepository.findByTraining(any())).thenReturn(Optional.of(model));
//
//        // Act
//        predefinedTrainService.train("task1", user, metadata);
//
//        // Assert
//        ArgumentCaptor<AlgorithmConfiguration> captor = ArgumentCaptor.forClass(AlgorithmConfiguration.class);
//        verify(algorithmConfigurationRepository, atLeastOnce()).save(captor.capture());
//
//        String[] finalOptions = AlgorithmUtil.deserializeOptions(captor.getValue().getOptions());
//        assertThat(Arrays.asList(finalOptions), hasItems("-P", "100", "-I", "100", "-num-slots", "1", "-K", "0", "-M", "1.0", "-V", "0.001", "-S", "1"));
//    }
//}


