package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.custom_algorithm.AlgorithmParameterDTO;
import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmCreateRequest;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.accessibility.CustomAlgorithmAccessibility;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.AlgorithmImageRepository;
import com.cloud_ml_app_thesis.repository.CustomAlgorithmRepository;
import com.cloud_ml_app_thesis.repository.accessibility.AlgorithmAccessibilityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomAlgorithmService {

    private final MinioService minioService;

    private final CustomAlgorithmRepository customAlgorithmRepository;

    private final ModelMapper modelMapper;

    private final BucketResolver bucketResolver;

    private final AlgorithmImageRepository algorithmImageRepository;

    private final AlgorithmImageRepository imageRepository;
    private final AlgorithmAccessibilityRepository algorithmAccessibilityRepository;

    ObjectMapper mapper = new ObjectMapper();

    public List<com.cloud_ml_app_thesis.dto.custom_algorithm.CustomAlgorithmDTO> getCustomAlgorithms(User currentUser) {
        List<CustomAlgorithm> algorithms = customAlgorithmRepository.findByOwnerOrPublic(currentUser);

        return algorithms.stream()
            .map(algorithm -> mapToDTO(algorithm, currentUser))
            .collect(Collectors.toList());
    }

    private com.cloud_ml_app_thesis.dto.custom_algorithm.CustomAlgorithmDTO mapToDTO(CustomAlgorithm algorithm, User currentUser) {
        // Get active image version
        String version = algorithm.getImages().stream()
            .filter(CustomAlgorithmImage::isActive)
            .findFirst()
            .map(CustomAlgorithmImage::getVersion)
            .orElse("Unknown");

        return com.cloud_ml_app_thesis.dto.custom_algorithm.CustomAlgorithmDTO.builder()
            .id(algorithm.getId())
            .name(algorithm.getName())
            .description(algorithm.getDescription())
            .version(version)
            .accessibility(algorithm.getAccessibility().getName().name())
            .ownerUsername(algorithm.getOwner().getUsername())
            .isOwner(algorithm.getOwner().getId().equals(currentUser.getId()))
            .keywords(algorithm.getKeywords())
            .createdAt(algorithm.getCreatedAt().toString())
            .build();
    }

    @Transactional
    public Integer create(CustomAlgorithmCreateRequest request, User user) {

        log.info("Creating new algorithm for user={}", user.getUsername());

        CustomAlgorithmAccessibility accessibility = algorithmAccessibilityRepository
                .findByName(request.getAccessibility())
                .orElseThrow(() -> new EntityNotFoundException("Invalid accessibility: " + request.getAccessibility()));


        CustomAlgorithm algorithm = new CustomAlgorithm();
        algorithm.setName(request.getName());
        algorithm.setDescription(request.getDescription());
        algorithm.setAccessibility(accessibility);
        algorithm.setKeywords(request.getKeywords());
        algorithm.setCreatedAt(LocalDateTime.now());
        algorithm.setOwner(user);

        customAlgorithmRepository.save(algorithm);

        List<CustomAlgorithmImage> activeImages = imageRepository.findByCustomAlgorithmAndIsActiveTrue(algorithm);
        activeImages.forEach(img -> img.setActive(false));
        imageRepository.saveAll(activeImages);

        CustomAlgorithmImage image = new CustomAlgorithmImage();
        image.setCustomAlgorithm(algorithm);
        image.setUploadedAt(ZonedDateTime.now());
        image.setVersion(request.getVersion());
        image.setActive(true);

        String timestamp = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        if (request.getParametersFile() != null && !request.getParametersFile().isEmpty()) {
                //Πρεπει να ξανακανουμε deserialize το αρχειο για να βαλουμε τα δεδομενα στη βαση
                try(InputStream is = request.getParametersFile().getInputStream()) {
                    List<AlgorithmParameterDTO> parametersDTO = mapper.readValue(is, new TypeReference<>() {
                    });
                    parametersDTO.forEach(p ->
                            log.info("📌 Param: name={}, type={}, default={}, range={}",
                                    p.getName(), p.getDescription(), p.getType(), p.getValue(), p.getRange())
                    );
                    List<AlgorithmParameter> parameters = parametersDTO.stream().map(dto -> {
                        AlgorithmParameter parameter = modelMapper.map(dto, AlgorithmParameter.class);
                        parameter.setAlgorithm(algorithm);
                        return parameter;
                    }).collect(Collectors.toCollection(ArrayList::new));
                    parameters.forEach(p ->
                            log.info("🧨 Param: name:{}, type={}",
                                    p.getName(), p.getDescription()));
                    algorithm.getParameters().clear();
                    algorithm.getParameters().addAll(parameters);
                    log.info("Parsed {} parameters", parameters.size());
                }catch(IOException e){
                    log.error("❌ Failed to parse parameters JSON", e);
                    throw new FileProcessingException("Could not read parameters JSON file ", e);
            }
        }else{
            throw new BadRequestException("Parameters file is required");
        }

        log.info("Algorithm Parameters={}", algorithm.getParameters());

        customAlgorithmRepository.save(algorithm);

        if(request.getDockerTarFile() != null && !request.getDockerTarFile().isEmpty()) {
            String objectName = user.getUsername() +  "_" + timestamp + "_" + request.getDockerTarFile().getOriginalFilename();
            String bucket = bucketResolver.resolve(BucketTypeEnum.CUSTOM_ALGORITHM);
            try {
                minioService.uploadObjectToBucket(request.getDockerTarFile(), bucket, objectName);
            } catch (IOException e) {
                throw new FileProcessingException("Failed to upload object to bucket", e);
            }
            image.setDockerTarKey(objectName);
            log.info("📤 Docker TAR uploaded to MinIO: {}/{}", bucket, objectName);
        } else if (StringUtils.isNotBlank(request.getDockerHubUrl())) {
            image.setDockerHubUrl(request.getDockerHubUrl());
            log.info("🔗 Docker image via DockerHub: {}", request.getDockerHubUrl());
        } else {
            throw new BadRequestException("Either dockerTarFile or dockerHubUrl must be provided");
        }

        algorithm.getImages().add(image);

        algorithmImageRepository.save(image);

        return algorithm.getId();
    }


}
