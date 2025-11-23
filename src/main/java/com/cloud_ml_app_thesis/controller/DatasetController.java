package com.cloud_ml_app_thesis.controller;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.dataset.*;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.enumeration.DatasetFunctionalTypeEnum;
import com.cloud_ml_app_thesis.enumeration.UserRoleEnum;

import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.service.DatasetService;
import com.cloud_ml_app_thesis.service.DatasetShareService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//TODO Apply Auth logic in the endpoint and decide either to pass the roles and username from Controller to Service either get them in the Service

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("api/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;
    private final DatasetShareService datasetShareService;
    private final UserRepository userRepository;

    @PostMapping("/search")
    public ResponseEntity<Page<Dataset>> searchDatasets(
            @RequestBody DatasetSearchRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "uploadDate") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        return ResponseEntity.ok(datasetService.searchDatasets(request, page, size, sortBy, sortDirection));
    }


    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> uploadDataset(@AuthenticationPrincipal UserDetails userDetails, @ModelAttribute DatasetUploadRequest request) {
        String username = userDetails.getUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));

        //TODO resolve the info the user may have provided or make another endpoint
        GenericResponse<?> datasetResponse = datasetService.uploadDataset(request.getFile(), user, request.getFunctionalType());
        if (datasetResponse.getErrorCode() != null && !datasetResponse.getErrorCode().isBlank()) {
            return ResponseEntity.internalServerError().body(datasetResponse);
        }
        return ResponseEntity.ok().body(datasetResponse);
    }

    @PatchMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenericResponse<?>> updateDataset(@AuthenticationPrincipal AccountDetails userDetails, @ModelAttribute DatasetUpdateRequest request) {
        //TODO why is DatasetUpdateRequest request haivng only the file? should igve id of the update dataset and more.
        String username = userDetails.getUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found with username: " + username));
        //TODO change 3rd argument here
        GenericResponse<?> response = datasetService.uploadDataset(request.getFile(), user, DatasetFunctionalTypeEnum.TRAIN);
        if (response.getErrorCode() != null && !response.getErrorCode().isBlank()) {
            return ResponseEntity.internalServerError().body(response);
        }
        return ResponseEntity.ok().body(response);
    }

    @GetMapping
    public ResponseEntity<GenericResponse<?>> getDatasets(@AuthenticationPrincipal AccountDetails userDetails) {
        String username = null;
        if(userDetails != null){
            username = userDetails.getUsername();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            // Admins and dataset managers can see all datasets
            if(roles.contains(UserRoleEnum.DATASET_MANAGER.toString()) || roles.contains(UserRoleEnum.ADMIN.toString())){
                username = null; // null = return all datasets for admins
            }
        }
        GenericResponse<?> response = datasetService.getDatasets(username);
        if (response.getErrorCode() != null && !response.getErrorCode().isBlank()) {
            return ResponseEntity.internalServerError().body(response);
        }
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/infos/{id}")
    public ResponseEntity<GenericResponse<?>> getDatasetsInfo(@AuthenticationPrincipal UserDetails userDetails, @PathVariable String id ) {
        String username = null;
        if(userDetails != null){
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            if(roles.contains(UserRoleEnum.DATASET_MANAGER.toString()) || roles.contains(UserRoleEnum.ADMIN.toString())){

            }
        }
        GenericResponse<?> response = datasetService.getDatasets(username);
        if (response.getErrorCode() != null && !response.getErrorCode().isBlank()) {
            return ResponseEntity.internalServerError().body(response);
        }
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GenericResponse<?>> getDataset(@PathVariable String id) {

        GenericResponse<?> response = null;
        if (response.getErrorCode() != null && !response.getErrorCode().isBlank()) {
            return ResponseEntity.internalServerError().body(response);
        }
        return ResponseEntity.ok().body(response);
    }
    @GetMapping("/info/{id}")
    public ResponseEntity<GenericResponse<?>> getDatasetInfo(@PathVariable String id) {

        GenericResponse<?> response = null;
        if (response.getErrorCode() != null && !response.getErrorCode().isBlank()) {
            return ResponseEntity.internalServerError().body(response);
        }
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/download/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<org.springframework.core.io.ByteArrayResource> downloadDataset(
            @PathVariable Integer id,
            @AuthenticationPrincipal AccountDetails userDetails) {

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        org.springframework.core.io.ByteArrayResource resource = datasetService.downloadDataset(id, user);

        // Get dataset to get original filename
        //TODO: Improve this by returning filename from the service
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataset_" + id + ".csv")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(resource.contentLength())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteDataset(
            @PathVariable Integer id,
            @AuthenticationPrincipal AccountDetails userDetails) {

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        datasetService.deleteDataset(id, user);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{email}/category")
    public ResponseEntity<GenericResponse<?>> getDatasetsUrls(@PathVariable String email){
        GenericResponse<?> response = datasetService.getDatasetUrls(email);
        if (response.getErrorCode() != null && !response.getErrorCode().isBlank()) {
            return ResponseEntity.internalServerError().body(response);
        }
        return ResponseEntity.ok().body(response);
    }

}
