package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.dto.dataset_configuration.ConfiguredDatasetSelectTableDTO;
import com.cloud_ml_app_thesis.dto.request.dataset_configuration.DatasetConfigurationCreateRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.DatasetConfiguration;
import com.cloud_ml_app_thesis.enumeration.status.DatasetConfigurationStatusEnum;
import com.cloud_ml_app_thesis.enumeration.status.TrainingStatusEnum;
import com.cloud_ml_app_thesis.repository.DatasetConfigurationRepository;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.TrainingRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import javassist.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DatasetConfigurationService {
    private final DatasetRepository datasetRepository;
    private final DatasetConfigurationRepository datasetConfigurationRepository;
    private final TrainingRepository trainingRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(DatasetConfigurationService.class);

    @Autowired
    public DatasetConfigurationService(DatasetRepository datasetRepository, DatasetConfigurationRepository datasetConfigurationRepository
            , TrainingRepository trainingRepository, UserRepository userRepository,
                                       MinioService minioService, MinioClient minioClient, ObjectMapper objectMapper){
        this.datasetRepository = datasetRepository;
        this.datasetConfigurationRepository= datasetConfigurationRepository;
        this.userRepository= userRepository;
        this.trainingRepository = trainingRepository;
        this.objectMapper = objectMapper;


    }

    //TODO APO BIG SPY EINAI AFTO
    public Integer datasetConfiguration(DatasetConfigurationCreateRequest request){
        Dataset dataset = null;
        try {
            dataset = datasetRepository.findById(request.getId()).orElseThrow(() -> new NotFoundException("..."));
        } catch (NotFoundException e) {
            return null;
        }
        DatasetConfiguration datasetConfiguration = new DatasetConfiguration( request.getBasicAttributesColumns(), request.getTargetClassColumn(), ZonedDateTime.now(ZoneId.of("Europe/Athens")), dataset);
        try {
            DatasetConfiguration uploadedDatasetConfiguration = datasetConfigurationRepository.save(datasetConfiguration);
            logger.info("Saved dataset configuration with ID {}", uploadedDatasetConfiguration.getId());
            return uploadedDatasetConfiguration.getId();
        } catch (DataAccessException e) {
            logger.error("Failed to save dataset configuration", e);
            //TODO Throw Custom Exception that will be handled from @ControllerAdvisor Class
            e.getMessage();
        }
        return null;
    }

    public ResponseEntity<GenericResponse<Map<String, Object>>> uploadDatasetConfiguration(Integer datasetId, String username,
                                                                                           String basicAttributesColumns, String targetClassColumn) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + username));

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + datasetId));

        if (!dataset.getUser().getUsername().equals(username)) {
            throw new IllegalArgumentException("Dataset does not belong to the specified user");
        }

        DatasetConfiguration datasetConfiguration = new DatasetConfiguration(
                basicAttributesColumns,
                targetClassColumn,
                ZonedDateTime.now(ZoneId.of("Europe/Athens")),
                dataset
        );

        try {
            datasetConfigurationRepository.save(datasetConfiguration);
        } catch (DataAccessException e) {
            logger.error("Failed to save the Dataset Configuration for Dataset '{}' by user '{}'.", datasetId, username);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new GenericResponse<>(null, "DB_SAVE_ERROR", "Failed to save dataset configuration", null));
        }

        // Prepare response data
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("id", datasetConfiguration.getId());
        responseData.put("timestamp", ZonedDateTime.now(ZoneId.of("Europe/Athens")));

        return ResponseEntity.ok(new GenericResponse<>(responseData, "", "Your dataset configuration has been saved.", null));
    }
    public ResponseEntity<GenericResponse<?>> getDatasetConfigurations(String username){
        Optional<List<DatasetConfiguration>> datasetConfigurationsOptional = datasetConfigurationRepository.findAllByDatasetUserUsernameAndStatus(username, DatasetConfigurationStatusEnum.CUSTOM);

        if(datasetConfigurationsOptional.isPresent()){
            List<ConfiguredDatasetSelectTableDTO> configuredDatasetSelectTableDTOs = datasetConfigurationsOptional.get()
                    .stream()
                    .map(this::convertToConfiguredDatasetDTO)
                    .toList();
            return new ResponseEntity<GenericResponse<?>>(new GenericResponse<List<ConfiguredDatasetSelectTableDTO>>(configuredDatasetSelectTableDTOs, null, null, new Metadata()), HttpStatus.OK);
        }
        return new ResponseEntity<GenericResponse<?>>(new GenericResponse<String>("Could not find datasets for user '" + username + "'.", null, null, new Metadata()),HttpStatus.OK);

    }
    private ConfiguredDatasetSelectTableDTO convertToConfiguredDatasetDTO(DatasetConfiguration datasetConfiguration){
        Dataset dataset = datasetConfiguration.getDataset();

        //Setting the field from the Dateset class using ObjectMapper(because he most fields are from Dataset class)
        ConfiguredDatasetSelectTableDTO dto =  objectMapper.convertValue(dataset, ConfiguredDatasetSelectTableDTO.class);

        //Setting the rest filed from DatasetConfiguration class
        dto.setId(datasetConfiguration.getId());
        dto.setBasicAttributesColumns(datasetConfiguration.getBasicAttributesColumns());
        dto.setTargetColumn(datasetConfiguration.getTargetColumn());
        dto.setUploadDate(datasetConfiguration.getUploadDate());

        //Setting the training participation statistics
        long completeCount = trainingRepository.countByDatasetConfigurationIdAndStatus(dataset.getId(), TrainingStatusEnum.COMPLETED);
        dto.setCompleteTrainingCount(completeCount);

        long failedCount = trainingRepository.countByDatasetConfigurationIdAndStatus(dataset.getId(), TrainingStatusEnum.FAILED);
        dto.setFailedTrainingCount(failedCount);

        return dto;
    }
}