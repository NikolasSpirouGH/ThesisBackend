package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.custom_algorithm.AlgorithmParameterDTO;
import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmCreateRequest;
import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmSearchRequest;
import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmUpdateRequest;
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
import org.springframework.security.access.AccessDeniedException;
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

        boolean isOwner = algorithm.getOwner().getId().equals(currentUser.getId());

        return com.cloud_ml_app_thesis.dto.custom_algorithm.CustomAlgorithmDTO.builder()
            .id(algorithm.getId())
            .name(algorithm.getName())
            .description(algorithm.getDescription())
            .version(version)
            .accessibility(algorithm.getAccessibility().getName().name())
            .ownerUsername(algorithm.getOwner().getUsername())
            .isOwner(isOwner)
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

    public com.cloud_ml_app_thesis.dto.custom_algorithm.CustomAlgorithmDTO getCustomAlgorithmById(Integer id, User currentUser) {
        CustomAlgorithm algorithm = customAlgorithmRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Algorithm not found with id: " + id));

        // Check if user has access to this algorithm
        boolean isOwner = algorithm.getOwner().getId().equals(currentUser.getId());
        boolean isPublic = algorithm.getAccessibility().getName().name().equals("PUBLIC");

        if (!isOwner && !isPublic) {
            throw new AccessDeniedException("You do not have permission to access this algorithm");
        }

        return mapToDTO(algorithm, currentUser);
    }

    public List<com.cloud_ml_app_thesis.dto.custom_algorithm.CustomAlgorithmDTO> searchCustomAlgorithms(
            CustomAlgorithmSearchRequest request, User currentUser) {

        // Get algorithms the user has access to
        List<CustomAlgorithm> algorithms = customAlgorithmRepository.findByOwnerOrPublic(currentUser);

        // Apply filters
        return algorithms.stream()
            .filter(algo -> matchesSearchCriteria(algo, request))
            .map(algo -> mapToDTO(algo, currentUser))
            .collect(Collectors.toList());
    }

    private boolean matchesSearchCriteria(CustomAlgorithm algorithm, CustomAlgorithmSearchRequest request) {
        List<Boolean> matches = new ArrayList<>();

        // Simple keyword search across multiple fields
        if (StringUtils.isNotBlank(request.getKeyword())) {
            String keyword = request.getKeyword().toLowerCase();
            boolean keywordMatch = algorithm.getName().toLowerCase().contains(keyword)
                || (algorithm.getDescription() != null && algorithm.getDescription().toLowerCase().contains(keyword))
                || algorithm.getKeywords().stream().anyMatch(k -> k.toLowerCase().contains(keyword))
                || algorithm.getImages().stream()
                    .filter(CustomAlgorithmImage::isActive)
                    .anyMatch(img -> img.getVersion().toLowerCase().contains(keyword));
            matches.add(keywordMatch);
        }

        // Advanced search criteria
        if (StringUtils.isNotBlank(request.getName())) {
            matches.add(algorithm.getName().toLowerCase().contains(request.getName().toLowerCase()));
        }

        if (StringUtils.isNotBlank(request.getDescription())) {
            matches.add(algorithm.getDescription() != null &&
                algorithm.getDescription().toLowerCase().contains(request.getDescription().toLowerCase()));
        }

        if (StringUtils.isNotBlank(request.getVersion())) {
            boolean versionMatch = algorithm.getImages().stream()
                .filter(CustomAlgorithmImage::isActive)
                .anyMatch(img -> img.getVersion().toLowerCase().contains(request.getVersion().toLowerCase()));
            matches.add(versionMatch);
        }

        if (request.getAccessibility() != null) {
            matches.add(algorithm.getAccessibility().getName().name().equals(request.getAccessibility().name()));
        }

        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            boolean keywordsMatch = request.getKeywords().stream()
                .anyMatch(keyword -> algorithm.getKeywords().stream()
                    .anyMatch(k -> k.toLowerCase().contains(keyword.toLowerCase())));
            matches.add(keywordsMatch);
        }

        if (StringUtils.isNotBlank(request.getCreatedAtFrom())) {
            try {
                LocalDateTime from = LocalDateTime.parse(request.getCreatedAtFrom());
                matches.add(algorithm.getCreatedAt().isAfter(from) || algorithm.getCreatedAt().isEqual(from));
            } catch (Exception e) {
                log.warn("Invalid createdAtFrom date format: {}", request.getCreatedAtFrom());
            }
        }

        if (StringUtils.isNotBlank(request.getCreatedAtTo())) {
            try {
                LocalDateTime to = LocalDateTime.parse(request.getCreatedAtTo());
                matches.add(algorithm.getCreatedAt().isBefore(to) || algorithm.getCreatedAt().isEqual(to));
            } catch (Exception e) {
                log.warn("Invalid createdAtTo date format: {}", request.getCreatedAtTo());
            }
        }

        if (matches.isEmpty()) {
            return true; // No criteria specified, match all
        }

        // Apply search mode (AND vs OR)
        return request.getSearchMode() == CustomAlgorithmSearchRequest.SearchMode.AND
            ? matches.stream().allMatch(Boolean::booleanValue)
            : matches.stream().anyMatch(Boolean::booleanValue);
    }

    @Transactional
    public void updateCustomAlgorithm(Integer id, CustomAlgorithmUpdateRequest request, User currentUser) {
        CustomAlgorithm algorithm = customAlgorithmRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Algorithm not found with id: " + id));

        // Check if user is the owner
        if (!algorithm.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to update this algorithm");
        }

        // Update fields only if provided (not null)
        if (StringUtils.isNotBlank(request.getName())) {
            algorithm.setName(request.getName());
        }

        if (request.getDescription() != null) {
            algorithm.setDescription(request.getDescription());
        }

        if (request.getKeywords() != null) {
            algorithm.setKeywords(request.getKeywords());
        }

        // Update accessibility
        if (request.getAccessibility() != null) {
            CustomAlgorithmAccessibility accessibility = algorithmAccessibilityRepository
                .findByName(request.getAccessibility())
                .orElseThrow(() -> new EntityNotFoundException("Invalid accessibility: " + request.getAccessibility()));
            algorithm.setAccessibility(accessibility);
        }

        // Update active image version if provided
        if (StringUtils.isNotBlank(request.getVersion())) {
            algorithm.getImages().stream()
                .filter(CustomAlgorithmImage::isActive)
                .findFirst()
                .ifPresent(img -> img.setVersion(request.getVersion()));
        }

        customAlgorithmRepository.save(algorithm);
        log.info("Algorithm updated successfully: id={}, name={}", id, algorithm.getName());
    }

    @Transactional
    public void deleteCustomAlgorithm(Integer id, User currentUser) {
        CustomAlgorithm algorithm = customAlgorithmRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Algorithm not found with id: " + id));

        // Check if user is the owner
        if (!algorithm.getOwner().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You do not have permission to delete this algorithm");
        }

        // Clean up MinIO files if needed
        algorithm.getImages().forEach(image -> {
            if (StringUtils.isNotBlank(image.getDockerTarKey())) {
                try {
                    String bucket = bucketResolver.resolve(BucketTypeEnum.CUSTOM_ALGORITHM);
                    minioService.deleteObject(bucket, image.getDockerTarKey());
                    log.info("Deleted Docker TAR from MinIO: {}/{}", bucket, image.getDockerTarKey());
                } catch (Exception e) {
                    log.warn("Failed to delete Docker TAR from MinIO: {}", image.getDockerTarKey(), e);
                }
            }
        });

        customAlgorithmRepository.delete(algorithm);
        log.info("Algorithm deleted successfully: id={}, name={}", id, algorithm.getName());
    }

}
