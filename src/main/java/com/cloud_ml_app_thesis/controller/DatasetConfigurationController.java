package com.cloud_ml_app_thesis.controller;


import com.cloud_ml_app_thesis.dto.request.dataset_configuration.DatasetConfigurationCreateRequest;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.service.DatasetConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequiredArgsConstructor
@RequestMapping("api/dataset-configurations")
public class DatasetConfigurationController {

    private final DatasetConfigurationService datasetConfigurationService;

    @PostMapping("/upload-dataset-configuration")
    public ResponseEntity<GenericResponse<Map<String, Object>>> uploadDatasetConfiguration(@RequestParam("datasetId") String datasetId,
                                                                                           @RequestParam("username") String username,
                                                                                           @RequestParam("basicAttributesColumns") String basicAttributesColumns,
                                                                                           @RequestParam("targetClassColumn") String targetClassColumn) {
        try {
            Integer datasetIdInteger = Integer.parseInt(datasetId);
            ResponseEntity<GenericResponse<Map<String, Object>>> responseData = datasetConfigurationService.uploadDatasetConfiguration(
                    datasetIdInteger, username, basicAttributesColumns, targetClassColumn
            );

            return responseData ;

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(new GenericResponse<>(null, "NumberFormat Error","Invalid dataset ID format", null));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(new GenericResponse<>(null, "Server Error","Unexpected Error, please contact support.",null));
        }
    }

    @GetMapping("/configurations")
    public ResponseEntity<GenericResponse<Object>> getDatasetConfigurations(@RequestParam String username) {
        try {
            Object configurations = datasetConfigurationService.getDatasetConfigurations(username);

            return ResponseEntity.ok(new GenericResponse<>(configurations, "", "Dataset configurations fetched successfully", null));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new GenericResponse<>(null, "Server error" , "Unexpected error while fetching datasets", null));
        }
    }

    @PostMapping("/create-dataset-conf")
    public ResponseEntity<GenericResponse<Map<String, Object>>> datasetConfiguration(@Valid @RequestBody DatasetConfigurationCreateRequest request) {
        try {
            Integer id = datasetConfigurationService.datasetConfiguration(request);
            if (id != null) {
                return ResponseEntity.ok(new GenericResponse<>(Collections.singletonMap("id", id), "","Dataset configuration created", null));
            } else {
                return ResponseEntity.badRequest().body(new GenericResponse<>(Collections.singletonMap("id", null), "","Couldn't create the configured dataset", new Metadata()));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new GenericResponse<>(null, "Server error", "Unexpected error occurred", new Metadata()));
        }
    }
}


