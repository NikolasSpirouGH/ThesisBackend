package com.cloud_ml_app_thesis.strategy;

import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Strategy interface for custom algorithm execution.
 * Implementations handle the language-specific (or language-agnostic) aspects of
 * container training and prediction, while the orchestration layer remains shared.
 */
public interface CustomExecutionStrategy {

    /**
     * Prepare the data directory for training.
     * For PYTHON_TEMPLATE: copies train.py template, extracts algorithm.py from image.
     * For GENERIC_BYOC: no-op (dataset.csv and params.json are already placed by the orchestrator).
     */
    void prepareTrainingData(String dockerImageTag, Path dataDir, Path outputDir);

    /**
     * Execute the training container.
     * For PYTHON_TEMPLATE: runs 'python train.py' (overrides CMD).
     * For GENERIC_BYOC: uses the container's own ENTRYPOINT/CMD (no override).
     */
    void executeTraining(String dockerImageTag, Path dataDir, Path outputDir,
                         Consumer<String> jobNameCallback);

    /**
     * Collect training results from the output directory.
     * For PYTHON_TEMPLATE: expects .pkl model file.
     * For GENERIC_BYOC: accepts any model file format.
     * Both expect metrics.json.
     */
    TrainingResults collectTrainingResults(Path outputDir);

    /**
     * Prepare the data directory for prediction.
     * For PYTHON_TEMPLATE: copies predict.py template, extracts algorithm.py,
     *   copies model as trained_model.pkl, copies label_mapping and feature_columns.
     * For GENERIC_BYOC: copies model file with its original name, test data to DATA_DIR.
     */
    void preparePredictionData(String dockerImageTag, Path dataDir, Path outputDir,
                               Path modelFilePath, Path labelMappingPath,
                               Path featureColumnsPath, Path testDataPath);

    /**
     * Execute the prediction container.
     * Same pattern as training - PYTHON_TEMPLATE overrides CMD, GENERIC_BYOC does not.
     */
    void executePrediction(String dockerImageTag, Path dataDir, Path outputDir,
                           Consumer<String> jobNameCallback);

    /**
     * Collect prediction results from the output directory.
     * Both strategies expect a .csv or .arff file in outputDir.
     */
    File collectPredictionResults(Path outputDir);

    /**
     * Result holder for training output files.
     */
    record TrainingResults(
        File modelFile,
        File metricsFile,
        File labelMappingFile,
        File featureColumnsFile
    ) {}
}
