package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.dto.custom_algorithm.AlgorithmParameterDTO;
import com.cloud_ml_app_thesis.dto.custom_algorithm.CustomAlgorithmDTO;
import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmCreateRequest;
import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmSearchRequest;
import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmUpdateRequest;
import com.cloud_ml_app_thesis.dto.request.model.ModelSearchRequest;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.accessibility.CustomAlgorithmAccessibility;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.exception.BadRequestException;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.repository.AlgorithmImageRepository;
import com.cloud_ml_app_thesis.repository.CustomAlgorithmRepository;
import com.cloud_ml_app_thesis.repository.accessibility.AlgorithmAccessibilityRepository;
import com.cloud_ml_app_thesis.util.DateUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.oauth2.sdk.Request;
import com.nimbusds.oauth2.sdk.util.StringUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.internal.inject.Custom;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
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

    private final DateUtil dateUtil;

    private final AlgorithmImageRepository algorithmImageRepository;

    private final AlgorithmImageRepository imageRepository;
    private final AlgorithmAccessibilityRepository algorithmAccessibilityRepository;

    ObjectMapper mapper = new ObjectMapper();

    public List<com.cloud_ml_app_thesis.dto.custom_algorithm.CustomAlgorithmDTO> getCustomAlgorithms(User currentUser) {
        // Check if user has ADMIN role
        boolean isAdmin = currentUser.getRoles().stream()
                .anyMatch(role -> role.getName().name().equals("ADMIN"));

        // Admin sees ALL algorithms, regular users see only their own + public ones
        List<CustomAlgorithm> algorithms = isAdmin
                ? customAlgorithmRepository.findAll()
                : customAlgorithmRepository.findByOwnerOrPublic(currentUser);

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

    //If admin should return all the algorithms even if is private or public. If is simple user should filter accessibility
    public List<CustomAlgorithmDTO> searchCustomAlgorithms(CustomAlgorithmSearchRequest request, User user) {

        // Check if user has ADMIN role
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName().name().equals("ADMIN"));

        // Admin sees ALL algorithms, regular users see only their own + public ones
        List<CustomAlgorithm> algorithms = isAdmin
                ? customAlgorithmRepository.findAll()
                : customAlgorithmRepository.findByOwnerOrPublic(user);

        log.info("Search Custom Algorithms: {} (isAdmin: {})", algorithms.size(), isAdmin);

        List<CustomAlgorithmDTO> results = algorithms.stream()
                .filter(algorithm -> matchCriteria(algorithm, request))
                .map(algorithm -> mapToDTO(algorithm, user))
                .collect(Collectors.toList());

        return results;
    }

    public boolean matchCriteria(CustomAlgorithm algorithm, CustomAlgorithmSearchRequest request) {
        List<Boolean> matches = new ArrayList<>();

        //Simple search
        if(StringUtils.isNotBlank(request.getSimpleSearchInput()) && !request.getSimpleSearchInput().equalsIgnoreCase("string")) {
            String searchInput = request.getSimpleSearchInput().toLowerCase();
            boolean searchMatch = algorithm.getName().toLowerCase().contains(searchInput)
                || (algorithm.getDescription() != null && algorithm.getDescription().toLowerCase().contains(searchInput))
                || algorithm.getKeywords().stream().anyMatch(k -> k.toLowerCase().contains(searchInput))
                || algorithm.getAccessibility().getName().name().toLowerCase().equals(searchInput) ;
            matches.add(searchMatch);
        }

        // Advanced Search - name AND description
        if(StringUtils.isNotBlank(request.getName()) && !request.getName().equalsIgnoreCase("string")) {
            boolean nameMatch = algorithm.getName().toLowerCase().contains(request.getName().toLowerCase());
            matches.add(nameMatch);
            log.info("  ➜ Name '{}' match: {}", request.getName(), nameMatch);
        }

        if(StringUtils.isNotBlank(request.getDescription()) && !request.getDescription().equalsIgnoreCase("string")) {
            boolean descMatch = algorithm.getDescription() != null &&
                algorithm.getDescription().toLowerCase().contains(request.getDescription().toLowerCase());
            matches.add(descMatch);
            log.info("  ➜ Description '{}' match: {}", request.getDescription(), descMatch);
        }

        // Date range filter - combine FROM and TO with AND logic
        boolean hasDateFrom = StringUtils.isNotBlank(request.getCreatedAtFrom()) && !request.getCreatedAtFrom().equalsIgnoreCase("string");
        boolean hasDateTo = StringUtils.isNotBlank(request.getCreatedAtTo()) && !request.getCreatedAtTo().equalsIgnoreCase("string");

        if(hasDateFrom || hasDateTo) {
            if(algorithm.getCreatedAt() == null) {
                // If createdAt is null, date filter always fails
                matches.add(false);
                log.info("  ➜ Date range match: false (algorithm createdAt is null)");
            } else {
                LocalDateTime createdAt = algorithm.getCreatedAt();
                boolean dateMatch = true;

                // Check FROM date
                if(hasDateFrom) {
                    try {
                        LocalDateTime from = dateUtil.parseDateTime(request.getCreatedAtFrom(), true);
                        boolean fromMatch = createdAt.isAfter(from) || createdAt.isEqual(from);
                        dateMatch = dateMatch && fromMatch;
                        log.info("  ➜ CreatedAtFrom '{}' match: {} (algo date: {})",
                                request.getCreatedAtFrom(), fromMatch, algorithm.getCreatedAt());
                    } catch (Exception e) {
                        log.warn("Invalid createdAtFrom format: {}", request.getCreatedAtFrom(), e);
                        dateMatch = false;
                    }
                }

                // Check TO date
                if(hasDateTo) {
                    try {
                        LocalDateTime to = dateUtil.parseDateTime(request.getCreatedAtTo(), false);
                        boolean toMatch = createdAt.isBefore(to) || createdAt.isEqual(to);
                        dateMatch = dateMatch && toMatch;
                        log.info("  ➜ CreatedAtTo '{}' match: {} (algo date: {})",
                                request.getCreatedAtTo(), toMatch, algorithm.getCreatedAt());
                    } catch (Exception e) {
                        log.warn("Invalid createdAtTo format: {}", request.getCreatedAtTo(), e);
                        dateMatch = false;
                    }
                }

                matches.add(dateMatch);
                log.info("  ➜ Date range overall match: {}", dateMatch);
            }
        }
        if(matches.isEmpty()){
            return true;
        }

        log.info("matches found: {}",matches.stream().allMatch(Boolean::booleanValue));

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
