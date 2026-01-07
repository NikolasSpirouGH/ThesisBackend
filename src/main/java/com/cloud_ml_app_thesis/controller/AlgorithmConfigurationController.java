package com.cloud_ml_app_thesis.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_ml_app_thesis.dto.request.algorithm_configuration.AlgorithmConfigurationCreateRequest;
import com.cloud_ml_app_thesis.dto.request.algorithm_configuration.AlgorithmConfigurationUpdateRequest;
import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import com.cloud_ml_app_thesis.service.AlgorithmConfigurationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/algorithm-configurations")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AlgorithmConfigurationController {

    private final AlgorithmConfigurationService algorithmConfigurationService;

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> deleteAlgorithm(@AuthenticationPrincipal UserDetails userDetails, @PathVariable @Positive Integer id) {
        boolean deleted = algorithmConfigurationService.deleteAlgorithmConfiguration(id);
        if(!deleted){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{algoId}")
    public ResponseEntity<AlgorithmConfiguration> createAlgorithmConfiguration(@RequestBody AlgorithmConfigurationCreateRequest request, @PathVariable @Positive Integer algoId) {
        return  ResponseEntity.ok(algorithmConfigurationService.createAlgorithmConfiguration(request, algoId));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ALGORITHM_MANAGER', 'ADMIN')")
    public ResponseEntity<AlgorithmConfiguration> updateAlgorithm(@AuthenticationPrincipal UserDetails userDetails, @PathVariable @Positive Integer id, @Valid @RequestBody AlgorithmConfigurationUpdateRequest request) {
        List<String> userRoles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        if(userRoles.contains("ALGORITHM_MANAGER") && request.getNewId()!= null){
            throw new AccessDeniedException("Unauthorized: You don't have access to modify the id of the algorithm.");
        }

        return ResponseEntity.ok(algorithmConfigurationService.updateAlgorithmConfiguration(id, request));
    }

}
