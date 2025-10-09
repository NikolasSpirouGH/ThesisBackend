package com.cloud_ml_app_thesis.intergration.isolated;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_ml_app_thesis.dto.request.train.TrainingStartRequest;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.repository.AlgorithmRepository;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.util.orchestrator.TrainingOrchestrator;

/**
 * Isolated service-level test for Weka training.
 * This bypasses the HTTP layer and directly tests TrainingOrchestrator.
 *
 * Prerequisites:
 * - Database must be running with a predefined Weka algorithm
 * - Must run INSIDE the backend container where all services are available
 *
 * Run from HOST machine:
 *   docker exec thesis_backend ./mvnw test -Dtest=WekaServiceTrainingIT
 */
@SpringBootTest
@ActiveProfiles("docker")
public class WekaServiceTrainingIT {

    @Autowired
    private TrainingOrchestrator trainingOrchestrator;

    @Autowired
    private AlgorithmRepository algorithmRepository;

    @Autowired
    private TaskStatusRepository taskStatusRepository;

    @Autowired
    private UserRepository userRepository;

    private static final Integer WEKA_ALGORITHM_ID = 67;
    private static final String TRAINING_FILE_PATH = "/app/src/test/resources/datasets/SimpleKmeans/Clustering_-_Prediction__clustering_predict_csv_.csv";
    private static final String TEST_USERNAME = "bigspy";

    @Test
    void testWekaTrainingDirectly() throws Exception {
        assertNotNull(WEKA_ALGORITHM_ID, "Please set WEKA_ALGORITHM_ID in the test class");

        File trainingFile = new File(TRAINING_FILE_PATH);
        assertTrue(trainingFile.exists(), "Training file not found: " + TRAINING_FILE_PATH);

        System.out.println("Testing Weka training with algorithm ID: " + WEKA_ALGORITHM_ID);
        System.out.println("Using file: " + trainingFile.getAbsolutePath());

        // Verify algorithm exists
        var algorithm = algorithmRepository.findById(WEKA_ALGORITHM_ID)
                .orElseThrow(() -> new RuntimeException("Algorithm not found: " + WEKA_ALGORITHM_ID));

        System.out.println("Algorithm found: " + algorithm.getName());
        System.out.println("Algorithm class: " + algorithm.getClassName());

        // Get real user from database (not a detached entity)
        User testUser = userRepository.findByUsername(TEST_USERNAME)
                .orElseThrow(() -> new RuntimeException("User not found: " + TEST_USERNAME));

        // Convert file to MultipartFile
        MultipartFile multipartFile = new MockMultipartFile(
                "file",
                trainingFile.getName(),
                "text/csv",
                new FileInputStream(trainingFile)
        );

        // Create TrainingStartRequest (same as production)\
        TrainingStartRequest request = new TrainingStartRequest();
        request.setFile(multipartFile);
        request.setAlgorithmId(String.valueOf(WEKA_ALGORITHM_ID));
        request.setBasicCharacteristicsColumns(null);
        request.setTargetClassColumn(null);
        request.setOptions(null);

        // Execute training using orchestrator (just like production does)
        System.out.println("Executing training via orchestrator...");

        try {
            // This mimics the full production flow
            String taskId = trainingOrchestrator.handleTrainingRequest(request, testUser);
            System.out.println("Training started with taskId: " + taskId);

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
                        System.out.println("Training completed successfully!");
                        completed = true;
                        break;
                    } else if ("FAILED".equals(status)) {
                        String errorMsg = task.getErrorMessage();
                        System.err.println("Training failed with error: " + errorMsg);
                        fail("Training failed: " + errorMsg);
                    }
                }
            }

            if (!completed) {
                fail("Training did not complete within timeout");
            }

        } catch (Exception e) {
            System.err.println("Training failed with error:");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();

            // Re-throw to fail the test
            throw e;
        }
    }
}
