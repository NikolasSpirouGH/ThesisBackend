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

import com.cloud_ml_app_thesis.dto.request.execution.ExecuteRequest;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.entity.model.Model;
import com.cloud_ml_app_thesis.repository.TaskStatusRepository;
import com.cloud_ml_app_thesis.repository.UserRepository;
import com.cloud_ml_app_thesis.repository.model.ModelRepository;
import com.cloud_ml_app_thesis.util.orchestrator.PredictionOrchestrator;

/**
 * Isolated service-level test for Weka prediction.
 * This bypasses the HTTP layer and directly tests ModelExecutionService.
 *
 * Prerequisites:
 * - Database must be running with a trained Weka model
 * - Must run INSIDE the backend container where all services are available
 *
 * Run from HOST machine:
 *   docker exec thesis_backend ./mvnw test -Dtest=WekaServicePredictionIT
 */
@SpringBootTest
@ActiveProfiles("docker")
public class WekaServicePredictionIT {

    @Autowired
    private PredictionOrchestrator predictionOrchestrator;

    @Autowired
    private ModelRepository modelRepository;

    @Autowired
    private TaskStatusRepository taskStatusRepository;

    @Autowired
    private UserRepository userRepository;

    private static final Integer WEKA_MODEL_ID = 11; 
    private static final String PREDICTION_FILE_PATH = "/app/src/test/resources/datasets/Logistic_Regression/Logistic_Regression_-_Prediction__logreg_predict_csv_.csv"; // Path inside container
    private static final String TEST_USERNAME = "bigspy";

    @Test
    void testWekaPredictionDirectly() throws Exception {
        assertNotNull(WEKA_MODEL_ID, "❌ Please set WEKA_MODEL_ID in the test class");

        File predictionFile = new File(PREDICTION_FILE_PATH);
        assertTrue(predictionFile.exists(), "❌ Prediction file not found: " + PREDICTION_FILE_PATH);

        System.out.println("🔍 Testing Weka prediction with model ID: " + WEKA_MODEL_ID);
        System.out.println("📁 Using file: " + predictionFile.getAbsolutePath());

        // Verify model exists and load it eagerly
        Model model = modelRepository.findByIdWithTrainingDetails(WEKA_MODEL_ID)
                .orElseThrow(() -> new RuntimeException("Model not found: " + WEKA_MODEL_ID));

        System.out.println("✅ Model found: " + model.getName());
        System.out.println("📊 Algorithm: " + model.getTraining().getAlgorithmConfiguration().getAlgorithm().getName());
        System.out.println("⚙️ Algorithm class: " + model.getTraining().getAlgorithmConfiguration().getAlgorithm().getClassName());
        System.out.println("🔧 Options: " + model.getTraining().getAlgorithmConfiguration().getOptions());

        // Get real user from database (not a detached entity)
        User testUser = userRepository.findByUsername(TEST_USERNAME)
                .orElseThrow(() -> new RuntimeException("User not found: " + TEST_USERNAME));

        // Convert file to MultipartFile
        MultipartFile multipartFile = new MockMultipartFile(
                "predictionFile",
                predictionFile.getName(),
                "text/csv",
                new FileInputStream(predictionFile)
        );

        // Create ExecuteRequest (same as production)
        ExecuteRequest request = new ExecuteRequest();
        request.setModelId(WEKA_MODEL_ID);
        request.setPredictionFile(multipartFile);

        // Execute prediction using orchestrator (just like production does)
        System.out.println("🔮 Executing prediction via orchestrator...");

        try {
            // This mimics the full production flow
            String taskId = predictionOrchestrator.handlePrediction(request, testUser);
            System.out.println("✅ Prediction started with taskId: " + taskId);

            // Wait for async execution to complete
            int maxAttempts = 15;
            int delayMs = 2000;
            boolean completed = false;

            for (int i = 1; i <= maxAttempts; i++) {
                Thread.sleep(delayMs);

                var taskStatus = taskStatusRepository.findById(taskId);
                if (taskStatus.isPresent()) {
                    var task = taskStatus.get();
                    String status = task.getStatus().toString();
                    System.out.printf("⏳ Attempt %d: Task status: %s%n", i, status);

                    if ("COMPLETED".equals(status)) {
                        System.out.println("✅ Prediction completed successfully!");
                        completed = true;
                        break;
                    } else if ("FAILED".equals(status)) {
                        String errorMsg = task.getErrorMessage();
                        System.err.println("❌ Prediction failed with error: " + errorMsg);
                        fail("Prediction failed: " + errorMsg);
                    }
                }
            }

            if (!completed) {
                fail("Prediction did not complete within timeout");
            }

        } catch (Exception e) {
            System.err.println("❌ Prediction failed with error:");
            System.err.println("Error type: " + e.getClass().getName());
            System.err.println("Error message: " + e.getMessage());
            e.printStackTrace();

            // Re-throw to fail the test
            throw e;
        }
    }
}
