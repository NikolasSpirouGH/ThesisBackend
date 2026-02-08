package com.cloud_ml_app_thesis.strategy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.util.ContainerRunner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Python template execution strategy.
 * Injects standardized train.py/predict.py scripts into the container's data directory,
 * extracts algorithm.py from the user's Docker image, and runs 'python train.py'.
 * This is the legacy/current behavior for custom algorithms.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PythonTemplateExecutionStrategy implements CustomExecutionStrategy {

    private final ContainerRunner containerRunner;

    @Override
    public void prepareTrainingData(String dockerImageTag, Path dataDir, Path outputDir) {
        // Copy standardized train.py template to data directory
        try (InputStream trainTemplate = getClass().getResourceAsStream("/templates/train.py")) {
            if (trainTemplate == null) {
                throw new IllegalStateException("train.py template not found in resources");
            }
            Path trainPyPath = dataDir.resolve("train.py");
            Files.copy(trainTemplate, trainPyPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied standardized train.py template to {}", trainPyPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy train.py template", e);
        }

        // Extract algorithm.py from the user's Docker image to /data directory
        try {
            containerRunner.copyFileFromImage(dockerImageTag, "/app/algorithm.py", dataDir.resolve("algorithm.py"));
            log.info("Extracted algorithm.py from Docker image to /data");
        } catch (Exception e) {
            log.error("Failed to extract algorithm.py from Docker image: {}", e.getMessage());
            throw new RuntimeException("Could not extract algorithm.py from user's Docker image", e);
        }
    }

    @Override
    public void executeTraining(String dockerImageTag, Path dataDir, Path outputDir,
                                Consumer<String> jobNameCallback) {
        containerRunner.runTrainingContainer(dockerImageTag, dataDir, outputDir, jobNameCallback);
    }

    @Override
    public TrainingResults collectTrainingResults(Path outputDir) {
        try {
            File modelFile = Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".pkl"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No model file (.pkl) generated", null));

            File metricsFile = Files.walk(outputDir)
                    .filter(p -> p.getFileName().toString().equals("metrics.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No metrics file generated", null));

            File labelMappingFile = Files.walk(outputDir)
                    .filter(p -> p.getFileName().toString().equals("label_mapping.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);

            File featureColumnsFile = Files.walk(outputDir)
                    .filter(p -> p.getFileName().toString().equals("feature_columns.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);

            log.info("Collected training results: model={}, metrics={}, label_mapping={}, feature_columns={}",
                    modelFile.getName(), metricsFile.getName(),
                    labelMappingFile != null ? labelMappingFile.getName() : "none",
                    featureColumnsFile != null ? featureColumnsFile.getName() : "none");

            return new TrainingResults(modelFile, metricsFile, labelMappingFile, featureColumnsFile);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to scan output directory for training results", e);
        }
    }

    @Override
    public void preparePredictionData(String dockerImageTag, Path dataDir, Path outputDir,
                                      Path modelFilePath, Path labelMappingPath,
                                      Path featureColumnsPath, Path testDataPath) {
        // Copy standardized predict.py template to data directory
        try (InputStream predictTemplate = getClass().getResourceAsStream("/templates/predict.py")) {
            if (predictTemplate == null) {
                throw new IllegalStateException("predict.py template not found in resources");
            }
            Path predictPyPath = dataDir.resolve("predict.py");
            Files.copy(predictTemplate, predictPyPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied standardized predict.py template to {}", predictPyPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy predict.py template", e);
        }

        try {
            // Copy model as trained_model.pkl to output dir (expected by predict.py template)
            Files.copy(modelFilePath, outputDir.resolve("trained_model.pkl"), StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied model to {}/trained_model.pkl", outputDir);

            // Copy optional artifacts
            if (labelMappingPath != null && Files.exists(labelMappingPath)) {
                Files.copy(labelMappingPath, outputDir.resolve("label_mapping.json"), StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied label_mapping.json to output dir");
            }
            if (featureColumnsPath != null && Files.exists(featureColumnsPath)) {
                Files.copy(featureColumnsPath, outputDir.resolve("feature_columns.json"), StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied feature_columns.json to output dir");
            }

            // Copy test data
            Files.copy(testDataPath, dataDir.resolve("test_data.csv"), StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied test data to {}/test_data.csv", dataDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare prediction data", e);
        }

        // Extract algorithm.py from the user's Docker image
        try {
            containerRunner.copyFileFromImage(dockerImageTag, "/app/algorithm.py", dataDir.resolve("algorithm.py"));
            log.info("Extracted algorithm.py from Docker image for prediction");
        } catch (Exception e) {
            log.error("Failed to extract algorithm.py for prediction: {}", e.getMessage());
            throw new RuntimeException("Could not extract algorithm.py for prediction", e);
        }
    }

    @Override
    public void executePrediction(String dockerImageTag, Path dataDir, Path outputDir,
                                  Consumer<String> jobNameCallback) {
        containerRunner.runPredictionContainer(dockerImageTag, dataDir, outputDir, jobNameCallback);
    }

    @Override
    public File collectPredictionResults(Path outputDir) {
        try {
            return Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".csv") || p.toString().endsWith(".arff"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException("No valid .csv or .arff output found", null));
        } catch (IOException e) {
            throw new FileProcessingException("Failed to scan output directory for prediction results", e);
        }
    }
}
