package com.cloud_ml_app_thesis.intergration.isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_ml_app_thesis.dto.request.custom_algorithm.CustomAlgorithmCreateRequest;
import com.cloud_ml_app_thesis.entity.CustomAlgorithm;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.enumeration.accessibility.AlgorithmAccessibiltyEnum;
import com.cloud_ml_app_thesis.repository.CustomAlgorithmRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.service.CustomAlgorithmService;

/**
 * Isolated service-level test for Custom Algorithm creation.
 * This bypasses the HTTP layer and directly tests CustomAlgorithmService.
 *
 * Prerequisites:
 * - Database must be running
 * - Test resources must be available (parameters.json, docker tar file)
 * - Must run INSIDE the backend container where all services are available
 *
 * Run from HOST machine:
 *   docker exec thesis_backend ./mvnw test -Dtest=CreateCustomAlgorithmIT
 */
@SpringBootTest
@ActiveProfiles("docker")
public class CreateCustomAlgorithmIT {

    @Autowired
    private CustomAlgorithmService customAlgorithmService;

    @Autowired
    private CustomAlgorithmRepository customAlgorithmRepository;

    @Autowired
    private UserRepository userRepository;

    private static final String TEST_USERNAME = "bigspy";
    private static final String PARAMETERS_FILE_PATH = "/app/src/test/resources/logistic_regression/parameters.json";
    private static final String DOCKER_TAR_FILE_PATH = "/app/src/test/resources/logistic_regression/my-logistic-algorithm.tar";

    @Test
    @Transactional
    void testCreateCustomAlgorithmWithDockerTar() throws Exception {
        System.out.println("Testing Custom Algorithm creation with Docker TAR file");

        // Verify test resources exist
        File parametersFile = new File(PARAMETERS_FILE_PATH);
        assertTrue(parametersFile.exists(), "Parameters file not found: " + PARAMETERS_FILE_PATH);

        File dockerTarFile = new File(DOCKER_TAR_FILE_PATH);
        assertTrue(dockerTarFile.exists(), "Docker TAR file not found: " + DOCKER_TAR_FILE_PATH);

        System.out.println("Using parameters file: " + parametersFile.getAbsolutePath());
        System.out.println("Using Docker TAR file: " + dockerTarFile.getAbsolutePath());

        // Get real user from database (not a detached entity)
        User testUser = userRepository.findByUsername(TEST_USERNAME)
                .orElseThrow(() -> new RuntimeException("User not found: " + TEST_USERNAME));

        System.out.println("Test user found: " + testUser.getUsername());

        // Convert files to MultipartFile
        MultipartFile parametersMultipartFile = new MockMultipartFile(
                "parametersFile",
                parametersFile.getName(),
                "application/json",
                new FileInputStream(parametersFile)
        );

        MultipartFile dockerTarMultipartFile = new MockMultipartFile(
                "dockerTarFile",
                dockerTarFile.getName(),
                "application/x-tar",
                new FileInputStream(dockerTarFile)
        );

        // Create CustomAlgorithmCreateRequest (same as production)
        CustomAlgorithmCreateRequest request = CustomAlgorithmCreateRequest.builder()
                .name("Test Logistic Regression Algorithm")
                .description("Integration test for custom algorithm creation")
                .accessibility(AlgorithmAccessibiltyEnum.PRIVATE)
                .keywords(new ArrayList<>(List.of("test", "logistic", "regression")))
                .version("1.0.0-test")
                .parametersFile(parametersMultipartFile)
                .dockerTarFile(dockerTarMultipartFile)
                .dockerHubUrl(null)
                .build();

        // Execute algorithm creation
        System.out.println("Creating custom algorithm via service...");

        try {
            Integer algorithmId = customAlgorithmService.create(request, testUser);
            assertNotNull(algorithmId, "Algorithm ID should not be null");

            System.out.println("Custom algorithm created with ID: " + algorithmId);

            // Verify algorithm was saved to database
            CustomAlgorithm savedAlgorithm = customAlgorithmRepository.findById(algorithmId)
                    .orElseThrow(() -> new RuntimeException("Algorithm not found in database: " + algorithmId));

            // Verify algorithm properties
            assertEquals("Test Logistic Regression Algorithm", savedAlgorithm.getName());
            assertEquals("Integration test for custom algorithm creation", savedAlgorithm.getDescription());
            assertEquals(testUser.getId(), savedAlgorithm.getOwner().getId());
            assertEquals(List.of("test", "logistic", "regression"), savedAlgorithm.getKeywords());

            System.out.println("Algorithm name: " + savedAlgorithm.getName());
            System.out.println("Algorithm description: " + savedAlgorithm.getDescription());

            // Verify algorithm has parameters
            assertNotNull(savedAlgorithm.getParameters(), "Algorithm parameters should not be null");
            assertFalse(savedAlgorithm.getParameters().isEmpty(), "Algorithm should have parameters");

            System.out.println("Number of parameters: " + savedAlgorithm.getParameters().size());
            savedAlgorithm.getParameters().forEach(param ->
                    System.out.println("  - Parameter: " + param.getName() + " (type: " + param.getType() + ", default: " + param.getValue() + ")")
            );

            // Verify algorithm has at least one image
            assertNotNull(savedAlgorithm.getImages(), "Algorithm images should not be null");
            assertFalse(savedAlgorithm.getImages().isEmpty(), "Algorithm should have at least one image");

            // Verify active image
            var activeImage = savedAlgorithm.getImages().stream()
                    .filter(img -> img.isActive())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active image found"));

            assertEquals("1.0.0-test", activeImage.getVersion());
            assertNotNull(activeImage.getDockerTarKey(), "Docker TAR key should not be null");

            System.out.println("Active image version: " + activeImage.getVersion());
            System.out.println("Docker TAR key: " + activeImage.getDockerTarKey());

            System.out.println(" Custom algorithm creation test completed successfully!");

        } catch (Exception e) {
            System.err.println("Custom algorithm creation failed with error:");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();

            // Re-throw to fail the test
            throw e;
        }
    }

    @Test
    @Transactional
    void testCreateCustomAlgorithmWithDockerHub() throws Exception {
        System.out.println("Testing Custom Algorithm creation with DockerHub URL");

        // Verify test resources exist
        File parametersFile = new File(PARAMETERS_FILE_PATH);
        assertTrue(parametersFile.exists(), "Parameters file not found: " + PARAMETERS_FILE_PATH);

        System.out.println("Using parameters file: " + parametersFile.getAbsolutePath());

        // Get real user from database (not a detached entity)
        User testUser = userRepository.findByUsername(TEST_USERNAME)
                .orElseThrow(() -> new RuntimeException("User not found: " + TEST_USERNAME));

        System.out.println("Test user found: " + testUser.getUsername());

        // Convert parameters file to MultipartFile
        MultipartFile parametersMultipartFile = new MockMultipartFile(
                "parametersFile",
                parametersFile.getName(),
                "application/json",
                new FileInputStream(parametersFile)
        );

        // Create CustomAlgorithmCreateRequest with DockerHub URL
        CustomAlgorithmCreateRequest request = CustomAlgorithmCreateRequest.builder()
                .name("Test DockerHub Algorithm")
                .description("Integration test for custom algorithm creation via DockerHub")
                .accessibility(AlgorithmAccessibiltyEnum.PUBLIC)
                .keywords(new ArrayList<>(List.of("test", "dockerhub")))
                .version("1.0.0-dockerhub")
                .parametersFile(parametersMultipartFile)
                .dockerTarFile(null)
                .dockerHubUrl("docker.io/library/test-algorithm:latest")
                .build();

        // Execute algorithm creation
        System.out.println("Creating custom algorithm with DockerHub URL via service...");

        try {
            Integer algorithmId = customAlgorithmService.create(request, testUser);
            assertNotNull(algorithmId, "Algorithm ID should not be null");

            System.out.println("Custom algorithm created with ID: " + algorithmId);

            // Verify algorithm was saved to database
            CustomAlgorithm savedAlgorithm = customAlgorithmRepository.findById(algorithmId)
                    .orElseThrow(() -> new RuntimeException("Algorithm not found in database: " + algorithmId));

            // Verify algorithm properties
            assertEquals("Test DockerHub Algorithm", savedAlgorithm.getName());
            assertEquals("Integration test for custom algorithm creation via DockerHub", savedAlgorithm.getDescription());

            // Verify active image has DockerHub URL
            var activeImage = savedAlgorithm.getImages().stream()
                    .filter(img -> img.isActive())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active image found"));

            assertEquals("1.0.0-dockerhub", activeImage.getVersion());
            assertNotNull(activeImage.getDockerHubUrl(), "DockerHub URL should not be null");
            assertEquals("docker.io/library/test-algorithm:latest", activeImage.getDockerHubUrl());

            System.out.println("Active image version: " + activeImage.getVersion());
            System.out.println("DockerHub URL: " + activeImage.getDockerHubUrl());

            System.out.println(" Custom algorithm creation with DockerHub test completed successfully!");

        } catch (Exception e) {
            System.err.println("Custom algorithm creation failed with error:");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();

            // Re-throw to fail the test
            throw e;
        }
    }
}
