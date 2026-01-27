package com.cloud_ml_app_thesis.unit_tests.service;

import com.cloud_ml_app_thesis.dto.request.model.ModelUpdateRequest;
import com.cloud_ml_app_thesis.entity.Category;
import com.cloud_ml_app_thesis.entity.CategoryRequest;
import com.cloud_ml_app_thesis.dto.request.model.ModelUpdateRequest;
import com.cloud_ml_app_thesis.entity.Training;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.accessibility.ModelAccessibility;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.enumeration.accessibility.ModelAccessibilityEnum;
import com.cloud_ml_app_thesis.repository.CategoryRepository;
import com.cloud_ml_app_thesis.repository.accessibility.ModelAccessibilityRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.service.ModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(MockitoExtension.class)
public class ModelServiceTest {

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ModelAccessibilityRepository modelAccessibilityRepository;

    @InjectMocks
    private ModelService modelService;

    private User owner;
    private User nonOwner;
    private Model model;
    private Training training;
    private Category category;
    private ModelAccessibility privateAccessibility;
    private ModelAccessibility publicAccessibility;


    @BeforeEach
    void setup()  {
        owner = User.builder()
                .id(UUID.randomUUID())
                .username("owner")
                .email("owner@test.com")
                .build();

        nonOwner = User.builder()
                .id(UUID.randomUUID())
                .username("nonOwner")
                .email("nonOwner@test.com")
                .build();

        training = Training.builder()
                .id(1)
                .user(owner)
                .build();

        category = Category.builder()
                .id(1)
                .name("Test Category")
                .build();

        publicAccessibility= new ModelAccessibility();
        publicAccessibility.setId(1);
        publicAccessibility.setName(ModelAccessibilityEnum.PUBLIC);

        privateAccessibility = new ModelAccessibility();
        privateAccessibility.setId(2);
        privateAccessibility.setName(ModelAccessibilityEnum.PRIVATE);

        model = Model.builder()
                .id(1)
                .name("Test Model")
                .description("Test Description")
                .dataDescription("Test DataDescription")
                .training(training)
                .category(category)
                .accessibility(privateAccessibility)
                .finalized(true)
                .build();
    }

    // 1st User case => Normal Flow
    @Test
    void updateModel_asOwner_shouldSucceed() {
        //GIVEN
        ModelUpdateRequest request = new ModelUpdateRequest();
        request.setName("Updated Model Name");
        request.setDescription("Updated Description");
        request.setDataDescription("Updated DataDescription");
        request.setCategoryId(1);
        request.setPublic(false);

        //WHEN
        when(modelRepository.findById(1)).thenReturn(Optional.of(model));
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(modelAccessibilityRepository.findByName(ModelAccessibilityEnum.PRIVATE)).thenReturn(Optional.of(privateAccessibility));

        //ACT
        assertDoesNotThrow(() -> modelService.updateModel(1, request, owner));

        //ASSERT
        verify(modelRepository, times(1)).findById(1);
        verify(categoryRepository, times(1)).findById(1);
        verify(modelAccessibilityRepository, times(1)).findByName(ModelAccessibilityEnum.PRIVATE);
        verify(modelRepository, times(1)).save(model);

        assertEquals("Updated Model Name", model.getName());
        assertEquals("Updated Description", model.getDescription());
        assertEquals("Updated DataDescription", model.getDataDescription());
        assertEquals(privateAccessibility, model.getAccessibility());
    }

    // 2st Use Case => Unauthorized

    @Test
    void updateModel_asNonOwner_shouldFail() {
        ModelUpdateRequest request = new ModelUpdateRequest();
        request.setName("Updated Model Name");

        when(modelRepository.findById(1)).thenReturn(Optional.of(model));

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> modelService.updateModel(1, request, nonOwner));

        assertEquals("You do not own this model", exception.getMessage());

        verify(modelRepository, times(1)).findById(1);
        verify(modelRepository, never()).save(model);
    }

    @Test
    void updateModel_nonFinalizedModel_shouldFail() {
        model.setFinalized(false);
        ModelUpdateRequest request = new ModelUpdateRequest();
        request.setName("Updated Model Name");

        when(modelRepository.findById(1)).thenReturn(Optional.of(model));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> modelService.updateModel(1, request, owner));

        assertEquals("Cannot update a non-finalized model", exception.getMessage());

        verify(modelRepository, times(1)).findById(1);
        verify(modelRepository, never()).save(any(Model.class));

    }
}

