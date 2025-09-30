package com.cloud_ml_app_thesis.intergration.full_flow_IT;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.ClassPathResource;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Integration test against containerized backend.
 * Run: docker-compose up -d
 * Then: mvn test -Dtest=BasicFullWekaFlowIT
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BasicFullWekaFlowIT {

    String jwtToken ;
    String taskId;
    Integer modelId;
    Integer executionId;

    @BeforeEach
    void setUp() {
        // Connect to containerized backend
        RestAssured.port = Integer.parseInt(System.getProperty("test.port", "8080"));
        RestAssured.baseURI = System.getProperty("test.host", "http://localhost");
        loginAndSetToken();
    }

    @Test
    @Order(1)
    void shouldTrainDataWithWekaAlgorithm() throws IOException {
        File trainFile = new ClassPathResource("datasets/Logistic_Regression/Logistic_Regression_-_Training__logreg_train_csv_.csv").getFile();

        Response rawResponse = given()
                .auth().oauth2(jwtToken)
                .contentType(ContentType.MULTIPART)
                //.multiPart("modelId", 150)
                .multiPart("file", trainFile)
                .multiPart("algorithmId", 21)
          //      .multiPart("basicCharacteristicsColumns", "1,2,3")
          //      .multiPart("targetClassColumn", "5")
                .when()
                .post("/api/train/train-model")
                .then()
                .log().all()
                .extract().response();

        System.out.println("Training raw response: " + rawResponse.asPrettyString());

        taskId = rawResponse.jsonPath().getString("dataHeader");

        Assertions.assertNotNull(taskId, "❌ taskId is null after training request.");

        waitForTaskCompletion(taskId);

        modelId = given()
                .header("Authorization", "Bearer " + jwtToken)
                .when()
                .get("/api/tasks/{taskId}/model-id", taskId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getInt("dataHeader.modelId");

        Assertions.assertNotNull(modelId, "❌ modelId is null after training.");
        System.out.println("Model ID after completion: " + modelId);
    }

    @Test
    @Order(2)
    void shouldCategorizeModel() throws IOException {
        Assumptions.assumeTrue(modelId != null, "Skipping test because modelId is null");
        String finalizePayload = """
        {
          "name": "My Finalized Model",
          "keywords": ["custom", "finalized"],\
          "description": "This is a finalized version of the trained model.",
          "dataDescription": "Basic classification data",
          "categoryId": 1,
          "public": true
        }
        """;

        Response finalizedResponse = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.JSON)
                .body(finalizePayload)
                .post("/api/models/{modelId}/model", modelId)
                .then()
                .statusCode(200)
                .body("message", containsString("Model finalized successfully"))
                .extract()
                .response();

        Integer finalizedModelId = finalizedResponse.jsonPath().getInt("dataHeader");
        Assertions.assertNotNull(finalizedModelId, "Finalized model ID is null");

        modelId = finalizedModelId;
        System.out.println("Finalized Model Id: " + finalizedModelId);
    }

    @Test
    @Order(3)
    void shouldPredictModel() throws IOException {
        Assumptions.assumeTrue(modelId != null, "Skipping test because modelId was not initialized");
        File predictionFile = new ClassPathResource("datasets/Logistic_Regression/Logistic_Regression_-_Prediction__logreg_predict_csv_.csv").getFile();

        Response predictionResponse = given()
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("predictionFile", predictionFile)
                .multiPart("modelId", modelId, "text/plain")
                .when()
                .post("/api/model-exec/execute")
                .then()
                .statusCode(202)
                .body("message", containsString("Prediction started asynchronously. Track with taskId."))
                .extract()
                .response();
        String predictionTaskId = predictionResponse.jsonPath().getString("dataHeader");

        Assertions.assertNotNull(predictionTaskId, "❌ predictionTaskId is null");

        waitForTaskCompletion(predictionTaskId);

        executionId = given()
                .header("Authorization", "Bearer " + jwtToken)
                .get("/api/tasks/{taskId}/execution-id", predictionTaskId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getInt("dataHeader.executionId");

        Assertions.assertNotNull(executionId, " executionId is null");

        System.out.println("Execution ID: " + executionId);

    }

    @Test
    @Order(5)
    void shouldDownloadPredictionResultFile() {
        Assumptions.assumeTrue(modelId != null, "Skipping test because modelId is null");

        Assertions.assertNotNull(this.executionId, "❌ executionId is null");

        Response downloadResponse = given()
                .header("Authorization", "Bearer " + jwtToken)
                .get("/api/model-exec/{executionId}/result", this.executionId)
                .then()
                .statusCode(200)
                .extract()
                .response();

        byte[] predictionFile = downloadResponse.getBody().asByteArray();

        Assertions.assertTrue(predictionFile.length > 0, "❌ Downloaded prediction file is empty");

        System.out.println("📥 Prediction result downloaded successfully. Bytes: " + predictionFile.length);
    }

    private void waitForTaskCompletion(String taskId) {
        int maxTries = 15;
        int delayMillis = 4000;

        for (int i = 1; i <= maxTries; i++) {
            Response response = given()
                    .header("Authorization", "Bearer " + jwtToken)
                    .get("/api/tasks/" + taskId)
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();

            String status = response.jsonPath().getString("status");
            System.out.printf("⏳ Attempt %d: Task %s current status: %s%n", i, taskId, status);

            if ("COMPLETED".equalsIgnoreCase(status)) {
                System.out.printf("✅ Task %s reached status: %s%n", taskId, "COMPLETED");
                return;
            } else if ("FAILED".equalsIgnoreCase(status)) {
                String errorMessage = response.jsonPath().getString("errorMessage");
                System.err.println("❌ Task " + taskId + " failed with error: " + errorMessage);
                fail("❌ Task " + taskId + " failed. Error: " + errorMessage);
            }

            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("❌ Wait was interrupted");
            }
        }

        fail("❌ Task " + taskId + " did not reach expected status: " + "COMPLETED" + " within timeout.");
    }

    private void loginAndSetToken() {
        String loginPayload = """
        {
          "username": "bigspy",
          "password": "adminPassword"
        }
           """;

        Response response = given()
                .contentType(ContentType.JSON)
                .body(loginPayload)
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
        System.out.println("🔁 Login response: " + response.asPrettyString());

        jwtToken = response.jsonPath().getString("dataHeader.token");
        System.out.println("🔐 JWT token: " + jwtToken);
    }
}
