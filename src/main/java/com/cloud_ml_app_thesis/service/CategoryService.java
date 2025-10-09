package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.dto.category.CategoryDTO;
import com.cloud_ml_app_thesis.dto.category.CategoryRequestDTO;
import com.cloud_ml_app_thesis.dto.request.category.CategoryCreateRequest;
import com.cloud_ml_app_thesis.dto.request.category.CategoryDeleteRequest;
import com.cloud_ml_app_thesis.dto.request.category.CategoryUpdateRequest;
import com.cloud_ml_app_thesis.dto.response.Metadata;
import com.cloud_ml_app_thesis.dto.response.GenericResponse;
import com.cloud_ml_app_thesis.entity.*;
import com.cloud_ml_app_thesis.entity.dataset.Dataset;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.entity.status.CategoryRequestStatus;
import com.cloud_ml_app_thesis.enumeration.status.CategoryRequestStatusEnum;
import com.cloud_ml_app_thesis.repository.CategoryHistoryRepository;
import com.cloud_ml_app_thesis.repository.CategoryRepository;
import com.cloud_ml_app_thesis.repository.CategoryRequestRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.dataset.DatasetRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.repository.status.CategoryRequestStatusRepository;
import com.cloud_ml_app_thesis.util.ValidationUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Slf4j
public class CategoryService {
    private final CategoryRequestRepository categoryRequestRepository;
    private final CategoryHistoryRepository categoryHistoryRepository;
    private final CategoryRequestStatusRepository categoryRequestStatusRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ModelRepository modelRepository;
    private final DatasetRepository datasetRepository;
    private final ModelMapper modelMapper;

    @Transactional
    public GenericResponse<CategoryRequestDTO> createCategory(String username, CategoryCreateRequest request) {
        log.info("User '{}' is creating a new category: {}", username, request.getName());

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User could not be found"));

        Optional<Category> existingCategory = categoryRepository.findByName(request.getName());
        if (existingCategory.isPresent()) {
            log.warn("Category '{}' already exists", request.getName());
            throw new EntityExistsException("The category you requested for creation already exists.");
        }

        boolean isForce = request.isForce();
        Set<Category> parentCategories = new HashSet<>();

        Set<Integer> parentCategoryIds = request.getParentCategoryIds();
        if (parentCategoryIds != null) {
            for (Integer parentId : parentCategoryIds) {
                Category parent = categoryRepository.findById(parentId)
                        .orElseThrow(() -> new EntityNotFoundException("Parent category not found: " + parentId));
                parentCategories.add(parent);
            }
        }

        CategoryRequestStatusEnum statusEnum = CategoryRequestStatusEnum.PENDING;
        User processedBy = null;
        LocalDateTime requestedAt = LocalDateTime.now();
        LocalDateTime processedAt = null;

        if (isForce) {
            log.info("Force flag is set. Creating category directly by user '{}'.", username);
            Category category = new Category();
            category.setName(request.getName());
            category.setDescription(request.getDescription());
            category.setCreatedBy(user);
            category.setParentCategories(parentCategories);
            if (request.getId() != null) {
                category.setId(request.getId());
            }
            categoryRepository.save(category);
            statusEnum = CategoryRequestStatusEnum.APPROVED;
            processedBy = user;
            processedAt = requestedAt;
        }

        CategoryRequestStatus categoryRequestStatus = categoryRequestStatusRepository.findByName(statusEnum)
                .orElseThrow(() -> new EntityNotFoundException("Category status could not be found"));

        CategoryRequest categoryRequest = new CategoryRequest();
        categoryRequest.setName(request.getName());
        categoryRequest.setDescription(request.getDescription());
        categoryRequest.setStatus(categoryRequestStatus);
        categoryRequest.setRequestedBy(user);
        categoryRequest.setProcessedBy(processedBy);
        categoryRequest.setRequestedAt(requestedAt);
        categoryRequest.setProcessedAt(processedAt);
        categoryRequest.setParentCategories(parentCategories);

        categoryRequestRepository.save(categoryRequest);

        CategoryRequestDTO dto = mapCategoryRequestToDto(categoryRequest);

        log.info("Category request for '{}' submitted successfully by user '{}'.", request.getName(), username);
        return new GenericResponse<>(dto, null, "Category created successfully.", new Metadata());
    }

    @Transactional
    public GenericResponse<CategoryRequestDTO> approveCategoryRequest(String username, Integer requestId) {
        log.info("User '{}' is approving category request with ID {}", username, requestId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("User '{}' not found", username);
                    return new EntityNotFoundException("User not found");
                });

        CategoryRequest request = categoryRequestRepository.findById(requestId)
                .orElseThrow(() -> {
                    log.error("Category request ID '{}' not found", requestId);
                    return new EntityNotFoundException("Category request not found");
                });

        if (request.getStatus().getName() != CategoryRequestStatusEnum.PENDING) {
            log.warn("Attempt to approve non-pending category request ID {}", requestId);
            throw new IllegalStateException("Only pending requests can be approved");
        }

        if (categoryRepository.findByName(request.getName()).isPresent()) {
            log.warn("Duplicate category name '{}' on approval attempt", request.getName());
            throw new EntityExistsException("Category with the same name already exists");
        }

        // 💡 Defensive copy of parent categories to avoid shared collection reference
        Set<Category> copiedParentCategories = request.getParentCategories() != null
                ? new HashSet<>(request.getParentCategories())
                : new HashSet<>();

        // Create new Category
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setCreatedBy(user);
        category.setParentCategories(copiedParentCategories);

        // Save Category
        Category savedCategory = categoryRepository.save(category);

        // Update CategoryRequest
        request.setApprovedCategory(savedCategory);
        request.setProcessedAt(LocalDateTime.now());
        request.setProcessedBy(user);
        request.setStatus(
                categoryRequestStatusRepository.findByName(CategoryRequestStatusEnum.APPROVED)
                        .orElseThrow(() -> {
                            log.error("APPROVED status not found in DB");
                            return new EntityNotFoundException("APPROVED status not found");
                        })
        );

        // Save updated CategoryRequest
        categoryRequestRepository.save(request);

        CategoryRequestDTO dto = mapCategoryRequestToDto(request);
        log.info("Successfully approved category request ID {}. Category '{}' created.", requestId, category.getName());

        return new GenericResponse<>(dto, null, "Category approved successfully.", new Metadata());
    }

    @Transactional
    public GenericResponse<CategoryRequestDTO> rejectCategoryRequest(String username, Integer requestId, String rejectionReason) {
        log.info("User '{}' is rejecting category request with ID {}. Reason: {}", username, requestId, rejectionReason);

        CategoryRequest request = categoryRequestRepository.findById(requestId)
                .orElseThrow(() -> {
                    log.error("Category request ID '{}' not found", requestId);
                    return new EntityNotFoundException("Category request not found with ID: " + requestId);
                });

        if (!request.getStatus().getName().equals(CategoryRequestStatusEnum.PENDING)) {
            log.warn("Attempt to reject a non-pending request ID '{}'. Current status: {}", requestId, request.getStatus().getName());
            throw new IllegalArgumentException("Only pending requests can be rejected.");
        }

        User admin = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User '" + username + "' not found"));

        CategoryRequestStatus rejectedStatus = categoryRequestStatusRepository.findByName(CategoryRequestStatusEnum.REJECTED)
                .orElseThrow(() -> new EntityNotFoundException("Status 'REJECTED' not found"));

        request.setStatus(rejectedStatus);
        request.setRejectionReason(rejectionReason);
        request.setProcessedBy(admin);
        request.setProcessedAt(LocalDateTime.now());

        categoryRequestRepository.save(request);

        CategoryRequestDTO dto = mapCategoryRequestToDto(request);
        log.info("Category request ID {} successfully rejected by '{}'.", requestId, username);

        return new GenericResponse<>(dto, null, "Category request rejected successfully.", new Metadata());
    }

    @Transactional
    public GenericResponse<CategoryDTO> updateCategory(String username, Integer categoryId, CategoryUpdateRequest request) {
        log.info("User '{}' is attempting to update category with ID {}", username, categoryId);

        User editor = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        boolean dataModified = false;
        String oldValues = convertCategoryToJson(category);

        if (ValidationUtil.stringExists(request.getName())) {
            log.info("Updating name to '{}'", request.getName());
            category.setName(request.getName());
            dataModified = true;
        }

        if (ValidationUtil.stringExists(request.getDescription())) {
            log.info("Updating description");
            category.setDescription(request.getDescription());
            dataModified = true;
        }

        if (request.getNewParentCategoryIds() != null && !request.getNewParentCategoryIds().isEmpty()) {
            List<Category> newParents = findCategoriesByIdsStrict(request.getNewParentCategoryIds());
            for (Category newParent : newParents) {
                if (!category.getParentCategories().contains(newParent)) {
                    category.getParentCategories().add(newParent);
                    newParent.getChildCategories().add(category);
                    categoryRepository.save(newParent); // bidirectional
                }
            }
            dataModified = true;
            log.info("Added new parent categories");
        }

        if (request.getParentCategoryIdsToRemove() != null && !request.getParentCategoryIdsToRemove().isEmpty()) {
            category = removeParentCategoriesStrict(category, request.getParentCategoryIdsToRemove());
            dataModified = true;
            log.info("Removed specified parent categories");
        }

        if (!dataModified) {
            throw new IllegalArgumentException("At least one field must be provided for update.");
        }

        Category updated = categoryRepository.save(category);
        String newValues = convertCategoryToJson(updated);

        categoryHistoryRepository.save(CategoryHistory.builder()
                .category(updated)
                .editedBy(editor)
                .editedAt(LocalDateTime.now())
                .oldValues(oldValues)
                .newValues(newValues)
                .build());


        CategoryDTO dto = mapCategoryToDto(updated);

        log.info("Category with ID {} successfully updated.", updated.getId());

        return new GenericResponse<>(dto, null, "Category updated successfully.", new Metadata());
    }


    @Transactional
    public GenericResponse<Void> deleteCategory(String username, Integer categoryId) {
        log.info("User '{}' is attempting to delete category with ID {}", username, categoryId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Category categoryToDelete = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with ID: " + categoryId));

        categoryToDelete.setDeleted(true);

        // Handle models with this main category
        List<Model> models = modelRepository.findByCategory(categoryToDelete);
        Category closestParent = findClosestParent(categoryToDelete);
        if (!models.isEmpty()) {
            if (closestParent == null) {
                log.warn("Deletion denied: No valid parent found to reassign models.");
                throw new IllegalStateException("Cannot delete category: No valid parent category found.");
            }
            for (Model model : models) {
                log.info("Reassigning model '{}' from main category ID {} to parent ID {}", model.getId(), categoryId, closestParent.getId());
                model.setCategory(closestParent);
            }
        }

        // Handle datasets with this main category
        List<Dataset> datasets = datasetRepository.findByCategory(categoryToDelete);
        if (!datasets.isEmpty()) {
            if (closestParent == null) {
                log.warn("Deletion denied: No valid parent found to reassign datasets.");
                throw new IllegalStateException("Cannot delete category: No valid parent category found.");
            }
            for (Dataset dataset : datasets) {
                log.info("Reassigning dataset '{}' from main category ID {} to parent ID {}", dataset.getId(), categoryId, closestParent.getId());
                dataset.setCategory(closestParent);
            }
        }

        // Move child categories to closest parent
        for (Category child : new HashSet<>(categoryToDelete.getChildCategories())) {
            child.getParentCategories().remove(categoryToDelete);
            if (closestParent != null) {
                child.getParentCategories().add(closestParent);
            }
        }

        for (Category parent : categoryToDelete.getParentCategories()) {
            parent.getChildCategories().remove(categoryToDelete);
        }

        log.info("Deleting category '{}'", categoryToDelete.getName());
        categoryRepository.save(categoryToDelete);

        return new GenericResponse<>(null, null, "Category deleted successfully", new Metadata());
    }



    private CategoryDTO mapCategoryToDto(Category category) {
        CategoryDTO dto = modelMapper.map(category, CategoryDTO.class);
        dto.setCreatedByUsername(category.getCreatedBy().getUsername());
        dto.setParentCategoryIds(
                category.getParentCategories() != null
                        ? category.getParentCategories().stream().map(Category::getId).collect(Collectors.toSet())
                        : Set.of()
        );
        return dto;
    }

    private CategoryRequestDTO mapCategoryRequestToDto(CategoryRequest request) {
        CategoryRequestDTO dto = modelMapper.map(request, CategoryRequestDTO.class);
        dto.setRequestedByUsername(request.getRequestedBy().getUsername());
        dto.setProcessedByUsername(request.getProcessedBy() != null ? request.getProcessedBy().getUsername() : null);
        dto.setStatus(request.getStatus().getName().name());
        dto.setParentCategoryIds(
                request.getParentCategories() != null
                        ? request.getParentCategories().stream().map(Category::getId).collect(Collectors.toSet())
                        : Set.of()
        );
        return dto;
    }

    public Set<Integer> getChildCategoryIds(Integer categoryId, boolean deepSearch) {
        if(deepSearch) {
            Set<Integer> childCategoryIds = new HashSet<>();
            findChildCategories(categoryId, childCategoryIds);
            return childCategoryIds; // The same Set is updated recursively
        } else {
            return getDirectChildCategoryIds(categoryId);
        }
    }

    public Set<Integer> getDirectChildCategoryIds(Integer categoryId) {
        return categoryRepository.findByParentCategoriesId(categoryId)
                .stream()
                .map(Category::getId)
                .collect(Collectors.toSet());
    }

    private void findChildCategories(Integer categoryId, Set<Integer> childCategoryIds) {
        childCategoryIds.add(categoryId);
        Set<Category> children = categoryRepository.findByParentCategoriesId(categoryId);
        // Recursively find deeper child categories
        for (Category child : children) {
            findChildCategories(child.getId(), childCategoryIds);
        }
    }

    private int getCategoryLevel(Category category) {
        int level = 0;
        while (!category.getParentCategories().isEmpty()) {
            category = category.getParentCategories().iterator().next();
            level++;
        }
        return level;
    }

    public Category findClosestParent(Category category) {
        Set<Category> parentCategories = category.getParentCategories();

        if (parentCategories.isEmpty()) {
            return null; // No parent category available
        }

        // If there's only one parent, it's the closest one
        if (parentCategories.size() == 1) {
            return parentCategories.iterator().next();
        }

        // If multiple parents exist, find the one closest in hierarchy
        Category closestParent = null;
        int highestLevel = -1;

        for (Category parent : parentCategories) {
            int parentLevel = getCategoryLevel(parent);
            if (parentLevel > highestLevel) {
                highestLevel = parentLevel;
                closestParent = parent;
            }
        }

        return closestParent;
    }


    private List<Category> findCategoriesByIdsStrict(Set<Integer> categoryIds){
        List<Category> foundCategories = categoryRepository.findAllById(categoryIds);

        Set<Integer> foundCategoryIds = foundCategories.stream()
                .map(Category::getId)
                .collect(Collectors.toSet());

        Set<Integer> missingIds = categoryIds.stream()
                .filter(id -> !foundCategoryIds.contains(id))
                .collect(Collectors.toSet());

        if (!missingIds.isEmpty()) {
            throw new EntityNotFoundException("Categories not found for IDs: " + missingIds);
        }
        return foundCategories;
    }

    private Category removeParentCategoriesStrict(Category category, Set<Integer> parentIdsToRemove){
        if(category == null){
            throw new RuntimeException("Category cannot be null");
        }

        if(parentIdsToRemove == null || parentIdsToRemove.isEmpty()){
            throw new IllegalArgumentException("At least one parent category ID must be provided.");
        }

        Set<Category> parentsToRemove = category.getParentCategories().stream()
                .filter(parent -> parentIdsToRemove.contains(parent.getId()))
                .collect(Collectors.toSet());

        if (parentsToRemove.isEmpty()) {
            throw new IllegalArgumentException("No matching parent categories found to remove.");
        }

        //Child to Parent Relationship remove
        category.getParentCategories().removeAll(parentsToRemove);

        //Parent to Child Relationship remove
        for(Category parent : parentsToRemove){
            parent.getChildCategories().remove(category);
            categoryRepository.save(parent);
        }

        return category;
    }

    private String convertCategoryToJson(Category category) {
        try {
            CategoryDTO dto = mapCategoryToDto(category); // Your own mapper method
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize category to JSON", e);
            return "{}";
        }
    }

    public List<CategoryDTO> getAllCategories() {
        return categoryRepository.findAll().stream()
                .filter(category -> !category.isDeleted())
                .map(this::mapCategoryToDto)
                .collect(Collectors.toList());
    }
}
