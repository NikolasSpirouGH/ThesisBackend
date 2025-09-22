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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

//@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true"
})
public class BasicFullCustomFlowIT {

    @LocalServerPort
    int port;
    String jwtToken ;
    Integer modelId;
    String taskId;
    Integer algorithmId;
    Integer trainingId;
    Integer executionId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        loginAndSetToken();
    }

    @Test
    @Order(1)
    void shouldCreateCustomAlgorithm() throws IOException {
        File parametersFile = new ClassPathResource("parameters/defaults.json").getFile();
        File dockerTarFile = new ClassPathResource("algorithms/linear-algo.tar").getFile();

        Response response = given()
                .port(port)
                .auth().oauth2(jwtToken)
                .multiPart("name", "testalgo")
                .multiPart("description", "test description")
                .multiPart("version", "1.0.0")
                .multiPart("accessibility", "PUBLIC")
                .multiPart("keywords", "ml", "classification")
                .multiPart("parametersFile", parametersFile)
                .multiPart("dockerHubUrl", "paradoxsenpai/logreg:1.0.0")
                .when()
                .post("/api/algorithms/createCustomAlgorithm")
                .then()
                .log().all()
                .statusCode(200)
                .body("message", containsString("Algorithm created successfully"))
                .extract()
                .response();

        algorithmId = response.jsonPath().getInt("dataHeader");

        Assertions.assertNotNull(algorithmId, "Algorithm id is not null");

        System.out.println("Custom Algorithm created successfully with id: " + algorithmId);

    }

    @Test
    @Order(2)
    void shouldTrainCustomModel() throws IOException {
        Assumptions.assumeTrue(algorithmId != null, "Skipping test because algorithmId is null");

        File datasetFile = new ClassPathResource("datasets/four_cols_nominal_train.csv").getFile();

        Response rawResponse = given()
                .port(port)
                .auth().oauth2(jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("datasetFile", datasetFile)
                .multiPart("algorithmId", algorithmId)
                .when()
                .post("/api/train/custom")
                .then()
                .log().all()
                .extract()
                .response();

        System.out.println("Training raw response: " + rawResponse.asPrettyString());

        taskId = rawResponse.jsonPath().getString("dataHeader");

        Assertions.assertNotNull(taskId, "❌ taskId is null after training request.");

        waitForTaskCompletion(taskId);

        modelId = given()
                .port(port)
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

    //Finalized Model
    @Test
    @Order(3)
    void shouldCategorizeModel() {
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
                .port(port)
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

    //Use Finalized Model
    @Test
    @Order(4)
    void shouldPredictCustomModel() throws IOException {
        Assumptions.assumeTrue(modelId != null, "Skipping test because modelId was not initialized");

        File predictionFile = new ClassPathResource("datasets/prediction_nominal_custom.csv").getFile();

        Response predictionResponse = given()
                .port(port)
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
                 .port(port)
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
                .port(port)
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
                    .port(port)
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
                fail("❌ Task " + taskId + " failed.");
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
                .port(port)
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
