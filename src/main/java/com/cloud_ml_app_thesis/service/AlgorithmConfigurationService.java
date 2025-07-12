package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.dto.request.algorithm_configuration.AlgorithmConfigurationUpdateRequest;
import com.cloud_ml_app_thesis.entity.Algorithm;
import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import com.cloud_ml_app_thesis.dto.request.algorithm_configuration.AlgorithmConfigurationCreateRequest;
import com.cloud_ml_app_thesis.repository.AlgorithmConfigurationRepository;
import com.cloud_ml_app_thesis.repository.AlgorithmRepository;
import com.cloud_ml_app_thesis.util.ValidationUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AlgorithmConfigurationService {

    private AlgorithmRepository algorithmRepository;
    private AlgorithmConfigurationRepository algorithmConfigurationRepository;

    public boolean deleteAlgorithmConfiguration(Integer id){
        if(!algorithmConfigurationRepository.existsById(id)){
            return false;
        }
        algorithmConfigurationRepository.deleteById(id);
        return true;
    }
    public AlgorithmConfiguration createAlgorithmConfiguration(AlgorithmConfigurationCreateRequest request, int algoId) {
        Algorithm algorithm = algorithmRepository.findById(algoId).orElseThrow(() -> new EntityNotFoundException("Algorithm was not found"));

        AlgorithmConfiguration algorithmConfiguration = new AlgorithmConfiguration();
        algorithmConfiguration.setOptions(request.getOptions());
        algorithmConfiguration.setAlgorithm(algorithm);
        algorithmConfiguration = algorithmConfigurationRepository.save(algorithmConfiguration);

        return algorithmConfiguration;

    }

    public AlgorithmConfiguration updateAlgorithmConfiguration(Integer algorithmConfigurationId, AlgorithmConfigurationUpdateRequest request){
        AlgorithmConfiguration algorithmConfiguration = algorithmConfigurationRepository.findById(algorithmConfigurationId)
                .orElseThrow(() -> new EntityNotFoundException("Algorithm configuration could not be found"));

        boolean dataExist = false;
        Integer newId = request.getNewId();
        if(newId!=null){
            if(algorithmConfigurationRepository.existsById(newId)){
                throw new RuntimeException("An algorithm configuration with ID " + newId + " already exists.");
            }
            algorithmConfiguration.setId(newId);
            dataExist = true;
        }

        if(ValidationUtil.stringExists(request.getOptions())){
            algorithmConfiguration.setOptions(request.getOptions());
            dataExist = true;
        }

        if(!dataExist){
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }
        return  algorithmConfigurationRepository.save(algorithmConfiguration);
    }

}
