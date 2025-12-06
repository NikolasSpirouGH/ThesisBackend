package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.dto.request.algorithm.AlgorithmCreateRequest;
import com.cloud_ml_app_thesis.dto.request.algorithm.AlgorithmUpdateRequest;
import com.cloud_ml_app_thesis.dto.weka_algorithm.WekaAlgorithmDTO;
import com.cloud_ml_app_thesis.dto.weka_algorithm.WekaAlgorithmOptionDTO;
import com.cloud_ml_app_thesis.entity.Algorithm;
import com.cloud_ml_app_thesis.entity.AlgorithmConfiguration;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.repository.AlgorithmConfigurationRepository;
import com.cloud_ml_app_thesis.repository.AlgorithmRepository;
import com.cloud_ml_app_thesis.util.ValidationUtil;
import com.cloud_ml_app_thesis.util.WekaOptionsParser;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.core.Option;
import weka.core.OptionHandler;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlgorithmService {

    private final AlgorithmRepository algorithmRepository;
    private final AlgorithmConfigurationRepository algorithmConfigurationRepository;
    private static final Logger logger = LoggerFactory.getLogger(AlgorithmService.class);


    public Map<String, String> getWekaAlgorithms() {
        Map<String, String> wekaAlgoInfos = new HashMap<>();

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("weka.classifiers"))
                .setScanners(new SubTypesScanner()));

        Set<Class<? extends Classifier>> classes = reflections.getSubTypesOf(Classifier.class);

        for (Class<? extends Classifier> cls : classes) {
            try {
                Classifier instance = cls.getDeclaredConstructor().newInstance();
                String classifierName = cls.getSimpleName();

                // Retrieve the package path relative to "weka.classifiers"
                Package pkg = cls.getPackage();
                String packageName = pkg.getName();
                String relativePath = packageName.substring(packageName.indexOf("weka.classifiers") + "weka.classifiers".length());

                System.out.println("Package path: " + relativePath);

                // Check if globalInfo method is available
                Method globalInfoMethod = null;
                try {
                    globalInfoMethod = cls.getMethod("globalInfo");
                } catch (NoSuchMethodException e) {
                    System.out.println("No globalInfo() method for " + classifierName);
                }

                if (globalInfoMethod != null) {
                    String classifierInfo = (String) globalInfoMethod.invoke(instance);
                    System.out.println("Name: " + classifierName);
                    System.out.println("Description: " + classifierInfo);
                    wekaAlgoInfos.put(classifierName, classifierInfo);

                } else {
                    System.out.println("Name: " + classifierName + " (no description available)");
                    wekaAlgoInfos.put(classifierName, "(no description available)");
                }

                // Listing options if the classifier implements OptionHandler
                if (instance instanceof OptionHandler) {
                    OptionHandler optionHandler = (OptionHandler) instance;
                    Enumeration<Option> options = optionHandler.listOptions();
                    System.out.println("Options:");
                    while (options.hasMoreElements()) {
                        Option option = options.nextElement();
                        System.out.println("\t- " + option.synopsis() + " " + option.description());
                        wekaAlgoInfos.put(option.name(), option.description());
                    }
                }
                System.out.println("---");
            } catch (Exception e) {
                System.err.println("Error processing class " + cls.getName() + ": " + e.getMessage());
            }
        }
        return wekaAlgoInfos;
    }

    public List<WekaAlgorithmDTO> getAlgorithms() {
        return algorithmRepository.findAll()
                .stream()
                .map(algorithm -> WekaAlgorithmDTO.builder()
                        .id(algorithm.getId())
                        .name(algorithm.getName())
                        .build()
                )
                .toList();
    }

    //TODO check if it is necessary to exist
    public void chooseAlgorithm(Integer id, String options) {
        Optional<Algorithm> algorithm = algorithmRepository.findById(id);
        AlgorithmConfiguration algorithmConfiguration = new AlgorithmConfiguration();
        algorithmConfiguration.setOptions(options);
        algorithmConfiguration.setAlgorithm(algorithm.get());

        algorithmConfigurationRepository.save(algorithmConfiguration);
    }


    public boolean deleteAlgorithm(Integer id){
        if(!algorithmRepository.existsById(id)){
            return false;
        }
        algorithmRepository.deleteById(id);
        return true;
    }
    public Algorithm updateAlgorithm(Integer id, AlgorithmUpdateRequest request){
        Algorithm algorithm = algorithmRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Algorithm not found."));

        boolean dataExist = false;

        Integer newId = request.getNewId();
        if(newId != null){
            if(algorithmRepository.existsById(newId)){
                throw new RuntimeException("An algorithm with ID " + newId + " already exists.");
            }
            algorithm.setId(newId);
            dataExist = true;
        }
        if(ValidationUtil.stringExists(request.getName())){
            algorithm.setName(request.getName());
            dataExist = true;
        }
        if(ValidationUtil.stringExists(request.getDescription())){
            algorithm.setDescription(request.getDescription());
            dataExist = true;
        }
        if(ValidationUtil.stringExists(request.getOptions())){
            algorithm.setOptions(request.getOptions());
            dataExist = true;
        }
        if(ValidationUtil.stringExists(request.getOptionsDescription())){
            algorithm.setOptionsDescription(request.getOptionsDescription());
            dataExist = true;
        }
        if(ValidationUtil.stringExists(request.getDefaultOptions())){
            algorithm.setDefaultOptions(request.getDefaultOptions());
            dataExist = true;
        }
        if(ValidationUtil.stringExists(request.getClassName())){
            algorithm.setClassName(request.getClassName());
            dataExist = true;
        }

        if(!dataExist){
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }
        return algorithmRepository.save(algorithm);
    }


    public Algorithm createAlgorithm(AlgorithmCreateRequest request){
        // Mapping DTO to Entity
        Algorithm algorithm = new Algorithm();
        algorithm.setName(request.getName());
        algorithm.setDescription(request.getDescription());
        algorithm.setOptions(request.getOptions());
        algorithm.setOptionsDescription(request.getOptionsDescription());
        algorithm.setDefaultOptions(request.getDefaultOptions());
        algorithm.setClassName(request.getClassName());

        // Save and return the saved entity
        return algorithmRepository.save(algorithm);
    }

    public AlgorithmConfiguration getAlgorithmConfiguration(Integer id) throws Exception {
        return algorithmConfigurationRepository.findById(id)
                .orElseThrow(() -> new Exception("Algorithm configuration ID " + id + " not found."));
    }


    public List<WekaAlgorithmDTO> searchWekaAlgorithm(String inputSearch, User user) {
        List<Algorithm> algorithms = algorithmRepository.findAll();

        if(!StringUtils.isNotBlank(inputSearch) || inputSearch.equals("")) {
            return algorithms.stream()
                    .map(algo -> mapToDTO(algo))
                    .collect(Collectors.toList());
        }
        return algorithms.stream()
                .filter(algo -> {
                    String searchLower = inputSearch.toLowerCase();
                    boolean nameMatch = algo.getName().toLowerCase().contains(searchLower);
                    boolean descriptionMatch = algo.getDescription() != null &&
                                              algo.getDescription().toLowerCase().contains(searchLower);
                    return nameMatch || descriptionMatch;
                })
                .map(algo -> mapToDTO(algo))  // Convert Algorithm to WekaAlgorithmDTO
                .collect(Collectors.toList());
    }

    // Helper method to convert to DTO
    private WekaAlgorithmDTO mapToDTO(Algorithm algorithm) {
        return WekaAlgorithmDTO.builder()
                .id(algorithm.getId())
                .name(algorithm.getName())
                .description(algorithm.getDescription())
                .build();
    }

    /**
     * Get algorithm by ID with parsed options
     */
    public WekaAlgorithmDTO getAlgorithmWithOptions(Integer id) {
        Algorithm algorithm = algorithmRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Algorithm not found with id: " + id));

        List<WekaAlgorithmOptionDTO> parsedOptions = WekaOptionsParser.parseOptions(
                algorithm.getOptions(),
                algorithm.getOptionsDescription(),
                algorithm.getDefaultOptions()
        );

        return WekaAlgorithmDTO.builder()
                .id(algorithm.getId())
                .name(algorithm.getName())
                .description(algorithm.getDescription())
                .options(parsedOptions)
                .defaultOptionsString(algorithm.getDefaultOptions())
                .build();
    }
}
