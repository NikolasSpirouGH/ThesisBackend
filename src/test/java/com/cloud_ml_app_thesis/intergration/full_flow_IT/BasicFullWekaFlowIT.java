package com.cloud_ml_app_thesis.intergration.full_flow_IT;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.io.IOException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true"
})
public class BasicFullWekaFlowIT {

    @LocalServerPort
    int port;

    String jwtToken ;
    String taskId;
    Integer modelId;
    Integer executionId;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
        loginAndSetToken();
    }

    @Test
    @Order(1)
    void shouldTrainDataWithWekaAlgorithm() throws IOException {
        File trainFile = new ClassPathResource("datasets/dataset2.csv").getFile();

        Response rawResponse = given()
                .auth().oauth2(jwtToken)
                .contentType(ContentType.MULTIPART)
                .multiPart("file", trainFile)
                .multiPart("algorithmId", 9)
                .multiPart("basicCharacteristicsColumns", "1,2,3")
                .multiPart("targetClassColumn", "5")
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

    @Test
    @Order(3)
    void shouldPredictModel() throws IOException {
        Assumptions.assumeTrue(modelId != null, "Skipping test because modelId was not initialized");
        File predictionFile = new ClassPathResource("datasets/prediction_nominal.csv").getFile();

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
