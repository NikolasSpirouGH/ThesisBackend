package com.cloud_ml_app_thesis.unit_tests.service;

import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;

import com.cloud_ml_app_thesis.entity.dataset.Dataset;

import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.model.ModelExecution;
import com.cloud_ml_app_thesis.repository.model.ModelExecutionRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.service.DatasetService;
import com.cloud_ml_app_thesis.service.ModelExecutionService;
import com.cloud_ml_app_thesis.service.ModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import weka.classifiers.Classifier;
import weka.core.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelExecutionServiceTest {

    @Mock private ModelRepository modelRepository;
    @Mock private ModelService modelService;
    @Mock private ModelExecutionRepository modelExecutionRepository;
    @Mock private DatasetService datasetService;
    @InjectMocks private ModelExecutionService modelExecutionService;

    @Mock private MultipartFile mockFile;
    private Classifier mockClassifier;
    private Instances mockInstances;

    @BeforeEach
    void setup() throws Exception {
        mockClassifier = mock(Classifier.class);

        // Define attributes: 2 numeric + 1 class
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("height"));         // numeric
        attributes.add(new Attribute("weight"));         // numeric
        attributes.add(new Attribute("class", List.of("thin", "normal", "obese")));  // nominal class

        //Capacity 0 giati tha ksekinisei me 0 rows to array list
        mockInstances = new Instances("TestDataset", attributes, 0);
        mockInstances.setClassIndex(2); // class attribute at index 2

        // Add 2 rows (instances)
        Instance instance1 = new DenseInstance(3);
        instance1.setValue(0, 180); // height
        instance1.setValue(1, 75);  // weight
        mockInstances.add(instance1);

        Instance instance2 = new DenseInstance(3);
        instance2.setValue(0, 160);
        instance2.setValue(1, 120);
        mockInstances.add(instance2);

        // Mock classifier behavior
        when(mockClassifier.classifyInstance(mockInstances.instance(0))).thenReturn(1.0); // normal
        when(mockClassifier.classifyInstance(mockInstances.instance(1))).thenReturn(2.0); // obese
    }


}
