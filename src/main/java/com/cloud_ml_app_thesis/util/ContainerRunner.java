package com.cloud_ml_app_thesis.util;

import java.nio.file.Path;

/**
 * Interface for running ML training and prediction containers.
 * Implementations:
 * - DockerContainerRunner: Uses Docker API (for Docker Compose)
 * - KubernetesJobRunner: Uses Kubernetes Jobs (for K8s deployments)
 */
public interface ContainerRunner {

    /**
     * Run a training container with the given image and paths (Python-based custom algorithms)
     */
    void runTrainingContainer(String imageName, Path containerDataDir, Path containerModelDir);

    /**
     * Run a prediction container with the given image and paths (Python-based custom algorithms)
     */
    void runPredictionContainer(String imageName, Path containerDataDir, Path containerModelDir);

    /**
     * Run a Weka training container (Java-based predefined algorithms)
     * Uses: java -jar weka-runner.jar train
     */
    void runWekaTrainingContainer(String imageName, Path containerDataDir, Path containerModelDir);

    /**
     * Run a Weka prediction container (Java-based predefined algorithms)
     * Uses: java -jar weka-runner.jar predict
     */
    void runWekaPredictionContainer(String imageName, Path containerDataDir, Path containerModelDir);

    /**
     * Load a Docker/container image from a TAR file
     */
    void loadImageFromTar(Path tarPath, String expectedTag);

    /**
     * Copy a file from a container image to the host filesystem
     */
    void copyFileFromImage(String imageName, String sourcePathInImage, Path destPathOnHost);

    /**
     * Pull/download a container image
     */
    void pullImage(String imageName);
}
