package com.cloud_ml_app_thesis.strategy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.util.ContainerRunner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generic BYOC (Bring Your Own Container) execution strategy.
 * Treats the user container as a black box:
 * - No scripts are injected, no files are extracted from the image.
 * - The container's own ENTRYPOINT/CMD is used.
 * - The platform only provides dataset, params, and environment variables (DATA_DIR, MODEL_DIR, EXECUTION_MODE).
 * - The container is responsible for reading input, executing its algorithm, and writing output files.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenericByocExecutionStrategy implements CustomExecutionStrategy {

    private static final Set<String> METADATA_FILES = Set.of(
            "metrics.json", "label_mapping.json", "feature_columns.json",
            "metadata.json", "prediction_metadata.json"
    );

    private final ContainerRunner containerRunner;

    @Override
    public void prepareTrainingData(String dockerImageTag, Path dataDir, Path outputDir) {
        // No-op for BYOC mode.
        // The orchestrator already places dataset.csv and params.json in DATA_DIR.
        // The container is responsible for reading them and running its own training logic.
        log.info("BYOC mode: no template injection. Container will use its own ENTRYPOINT.");
    }

    @Override
    public void executeTraining(String dockerImageTag, Path dataDir, Path outputDir,
                                Consumer<String> jobNameCallback) {
        containerRunner.runGenericTrainingContainer(dockerImageTag, dataDir, outputDir, jobNameCallback);
    }

    @Override
    public TrainingResults collectTrainingResults(Path outputDir) {
        try {
            // metrics.json is REQUIRED
            File metricsFile = Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("metrics.json"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException(
                            "No metrics.json found in BYOC container output. " +
                            "The container must write metrics.json to MODEL_DIR.", null));

            // Model file: any non-metadata, non-CSV regular file
            // Supports .pkl, .pt, .onnx, .h5, .bin, .ser, .joblib, .model, etc.
            File modelFile = Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> !METADATA_FILES.contains(p.getFileName().toString()))
                    .filter(p -> !p.getFileName().toString().endsWith(".csv"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException(
                            "No model file found in BYOC container output. " +
                            "The container must write a model file to MODEL_DIR.", null));

            // Optional artifacts
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

            log.info("Collected BYOC training results: model={}, metrics={}, label_mapping={}, feature_columns={}",
                    modelFile.getName(), metricsFile.getName(),
                    labelMappingFile != null ? labelMappingFile.getName() : "none",
                    featureColumnsFile != null ? featureColumnsFile.getName() : "none");

            return new TrainingResults(modelFile, metricsFile, labelMappingFile, featureColumnsFile);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to scan output directory for BYOC training results", e);
        }
    }

    @Override
    public void preparePredictionData(String dockerImageTag, Path dataDir, Path outputDir,
                                      Path modelFilePath, Path labelMappingPath,
                                      Path featureColumnsPath, Path testDataPath) {
        try {
            // Copy model file with its ORIGINAL name/extension to MODEL_DIR
            Files.copy(modelFilePath, outputDir.resolve(modelFilePath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied model file {} to output dir", modelFilePath.getFileName());

            // Copy test data to DATA_DIR as test_data.csv
            Files.copy(testDataPath, dataDir.resolve("test_data.csv"), StandardCopyOption.REPLACE_EXISTING);
            log.info("Copied test data to {}/test_data.csv", dataDir);

            // Copy optional artifacts if present
            if (labelMappingPath != null && Files.exists(labelMappingPath)) {
                Files.copy(labelMappingPath, outputDir.resolve("label_mapping.json"), StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied label_mapping.json to output dir");
            }
            if (featureColumnsPath != null && Files.exists(featureColumnsPath)) {
                Files.copy(featureColumnsPath, outputDir.resolve("feature_columns.json"), StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied feature_columns.json to output dir");
            }

            // NO predict.py template injection, NO algorithm.py extraction
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare BYOC prediction data", e);
        }
    }

    @Override
    public void executePrediction(String dockerImageTag, Path dataDir, Path outputDir,
                                  Consumer<String> jobNameCallback) {
        containerRunner.runGenericPredictionContainer(dockerImageTag, dataDir, outputDir, jobNameCallback);
    }

    @Override
    public File collectPredictionResults(Path outputDir) {
        try {
            return Files.walk(outputDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".csv") || p.toString().endsWith(".arff"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElseThrow(() -> new FileProcessingException(
                            "No valid .csv or .arff output found in BYOC container. " +
                            "The container must write predictions.csv to MODEL_DIR.", null));
        } catch (IOException e) {
            throw new FileProcessingException("Failed to scan output directory for BYOC prediction results", e);
        }
    }
}
