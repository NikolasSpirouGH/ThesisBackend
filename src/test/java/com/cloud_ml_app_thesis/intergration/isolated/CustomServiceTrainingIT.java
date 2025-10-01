package com.cloud_ml_app_thesis.intergration.isolated;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_ml_app_thesis.config.security.AccountDetails;
import com.cloud_ml_app_thesis.dto.request.train.CustomTrainRequest;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.repository.CustomAlgorithmRepository;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.util.orchestrator.TrainingOrchestrator;

/**
 * Isolated service-level test for Custom algorithm training.
 * This bypasses the HTTP layer and directly tests TrainingOrchestrator.
 *
 * Prerequisites:
 * - Database must be running with a custom algorithm
 * - Must run INSIDE the backend container where all services are available
 *
 * Run from HOST machine:
 *   docker exec thesis_backend ./mvnw test -Dtest=CustomServiceTrainingIT
 */
@SpringBootTest
@ActiveProfiles("docker")
public class CustomServiceTrainingIT {

    @Autowired
    private TrainingOrchestrator trainingOrchestrator;

    @Autowired
    private CustomAlgorithmRepository customAlgorithmRepository;

    @Autowired
    private TaskStatusRepository taskStatusRepository;

    @Autowired
    private UserRepository userRepository;

    private static final Integer CUSTOM_ALGORITHM_ID = 11;
    private static final String TRAINING_FILE_PATH = "/app/src/test/resources/datasets/Logistic_Regression/Logistic_Regression_-_Training__logreg_train_csv_.csv";
    private static final String TEST_USERNAME = "bigspy";

    void testCustomTrainingDirectly() throws Exception {
        assertNotNull(CUSTOM_ALGORITHM_ID, "Please set CUSTOM_ALGORITHM_ID in the test class");

        File trainingFile = new File(TRAINING_FILE_PATH);
        assertTrue(trainingFile.exists(), "Training file not found: " + TRAINING_FILE_PATH);

        System.out.println("Testing Custom algorithm training with algorithm ID: " + CUSTOM_ALGORITHM_ID);
        System.out.println("Using file: " + trainingFile.getAbsolutePath());

        // Verify custom algorithm exists
        var algorithm = customAlgorithmRepository.findById(CUSTOM_ALGORITHM_ID)
                .orElseThrow(() -> new RuntimeException("Custom algorithm not found: " + CUSTOM_ALGORITHM_ID));

        System.out.println("Algorithm found: " + algorithm.getName());

        // Get real user from database (not a detached entity)
        User testUser = userRepository.findByUsername(TEST_USERNAME)
                .orElseThrow(() -> new RuntimeException("User not found: " + TEST_USERNAME));

        // Convert file to MultipartFile
        MultipartFile multipartFile = new MockMultipartFile(
                "datasetFile",
                trainingFile.getName(),
                "text/csv",
                new FileInputStream(trainingFile)
        );

        // Create CustomTrainRequest (same as production)
        CustomTrainRequest request = CustomTrainRequest.builder()
                .algorithmId(CUSTOM_ALGORITHM_ID)
                .datasetFile(multipartFile)
                .parametersFile(null)
                .basicAttributesColumns(null)
                .targetColumn(null)
                .build();

        // Wrap user in AccountDetails (required by orchestrator)
        AccountDetails accountDetails = new AccountDetails(testUser);

        // Execute training using orchestrator (just like production does)
        System.out.println("Executing custom training via orchestrator...");

        try {
            // This mimics the full production flow
            String taskId = trainingOrchestrator.handleCustomTrainingRequest(request, accountDetails);
            System.out.println("Custom training started with taskId: " + taskId);

            // Wait for async execution to complete
            int maxAttempts = 30;
            int delayMs = 3000;
            boolean completed = false;

            for (int i = 1; i <= maxAttempts; i++) {
                Thread.sleep(delayMs);

                var taskStatus = taskStatusRepository.findById(taskId);
                if (taskStatus.isPresent()) {
                    var task = taskStatus.get();
                    String status = task.getStatus().toString();
                    System.out.printf("Attempt %d: Task status: %s%n", i, status);

                    if ("COMPLETED".equals(status)) {
                        System.out.println("Custom training completed successfully!");
                        completed = true;
                        break;
                    } else if ("FAILED".equals(status)) {
                        String errorMsg = task.getErrorMessage();
                        System.err.println("Custom training failed with error: " + errorMsg);
                        fail("Custom training failed: " + errorMsg);
                    }
                }
            }

            if (!completed) {
                fail("Custom training did not complete within timeout");
            }

        } catch (Exception e) {
            System.err.println("Custom training failed with error:");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();

            // Re-throw to fail the test
            throw e;
        }
    }
}
